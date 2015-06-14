(ns copycat-jepsen.core
  (:require [clojure [pprint :refer :all]
             [string :as str]]
            [clojure.java.io :as io]
            [clojure.tools.logging :refer [debug info warn]]
            [copycat-jepsen.util :as cutil]
            [jepsen [core      :as jepsen]
             [db        :as db]
             [util      :as util :refer [meh timeout]]
             [control   :as c :refer [|]]
             [client    :as client]
             [checker   :as checker]
             [model     :as model]
             [generator :as gen]
             [nemesis   :as nemesis]
             [store     :as store]
             [report    :as report]
             [tests     :as tests]]
            [jepsen.control [net :as net]
             [util :as net/util]]
            [jepsen.os.debian :as debian]
            [knossos.core :as knossos])
  (:import (net.kuujo.copycat CopycatClient)
           (net.kuujo.copycat.cluster NettyMembers NettyMember)
           (net.kuujo.copycat.atomic AsyncReference)))

(defn- node-id [node]
  (Integer/parseInt (subs (name node) 1)))

; Test lifecycle

(defn install!
  "Installs copycat on the given node."
  [node version]

  ; Install JDK 8
  (when (nil? (debian/installed-version "oracle-java8-installer"))
    (info node "installing JDK")
    (c/su
      (c/exec :echo (c/lit "\"deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main\" | tee /etc/apt/sources.list.d/webupd8team-java.list"))
      (c/exec :echo (c/lit "\"deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main\" | tee -a /etc/apt/sources.list.d/webupd8team-java.list"))
      (c/exec :echo (c/lit "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections"))
      (c/exec :apt-key (c/lit "adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886")))
    (debian/update!)
    (debian/install ["oracle-java8-installer" "oracle-java8-set-default"])

    ; Install other dependencies
    (debian/install ["git" "maven"]))

  ; Install copycat
  ;(c/su
  ;  (info node "fetching copycat")
  ;  (if-not (cutil/dir-exists "/opt/copycat")
  ;    (c/cd "/opt"
  ;          (c/exec :git :clone "https://github.com/kuujo/copycat.git"))
  ;    (c/cd "/opt/copycat"
  ;          (c/exec :git :pull)))
  ;  (info node "building copycat")
  ;  (c/cd "/opt/copycat"
  ;        ;(c/exec :git :checkout "0.6.0")
  ;        (c/exec :mvn :clean :install "-DskipTests=true"))
  ;  (c/cd "/opt/copycat/examples"
  ;        (c/exec :mvn :clean :package)))
  )

(defn start!
  "Starts copycat."
  [node test nodes]
  (let [other-nodes (apply merge
                           (map #(assoc {} % (disj nodes %))
                                nodes))
        local-node-arg (str (node-id node) ":5555")
        other-node-args (map #(str (node-id %) ":" (name %) ":5555")
                             (node other-nodes))
        ; temp haxxxx
        jarfile "/root/.m2/repository/net/kuujo/copycat/copycat-server-example/0.6.0-SNAPSHOT/copycat-server-example-0.6.0-SNAPSHOT-shaded.jar"]
    (info node "starting copycat")
    (meh (c/exec :rm (c/lit "/var/log/copycat.log")))
    (c/su
      (c/cd "/opt/copycat/examples/server"
            (c/exec :java :-jar jarfile local-node-arg other-node-args
                    (c/lit "2>> /dev/null >> /var/log/copycat.log & echo $!"))))
    ))

(defn stop!
  "Stops copycat."
  [node]
  (info node "stopping copycat")
  (c/su
    (meh (c/exec :pkill :-9 :java))))

(defn db
  "Copycat database"
  [version nodes]
  (reify db/DB
    (setup! [_ test node]
      (doto node
        (install! version)
        (start! test nodes)))

    (teardown! [_ test node]
      (stop! node))))

; Test clients

(defn connect
  "Returns a Copycat for the given node. Blocks until the client is available."
  [node nodes]
  (let [other-nodes (disj nodes node)
        cluster-members (map #(-> (NettyMember/builder)
                                  (.withId (node-id %))
                                  (.withHost (name %))
                                  (.withPort 5555)
                                  .build)
                             other-nodes)
        cluster (-> (NettyMembers/builder)
                    (.withMembers cluster-members)
                    (.build))
        client (-> (CopycatClient/builder)
                   (.withMembers cluster)
                   (.build))]
    client))

(defn setit!
  "Sets a value for a reference"
  [^AsyncReference reference value]
  (-> (.set reference value) (.join)))

(defn getit!
  "Gets a value from a reference"
  [^AsyncReference reference]
  (-> (.get reference) (.join)))

(defn cas!
  "Compares and sets a value for a reference"
  [^AsyncReference reference expected updated]
  (-> (.compareAndSet reference expected updated) (.join)))

(defrecord RegisterClient [client nodes]
  client/Client
  (setup! [this test node]
    (info "connecting to copyat node" (name node))
    (let [client (connect node nodes)]
      (assoc this :client client)
      (assoc this :resource (-> client
                                (.open)
                                (.get)
                                (.create "/register" AsyncReference)
                                (.get)))))

  (invoke! [this test op]
    (case (:f op)
      :read (assoc op :type :ok
                      :value (:resource this))

      :add (do (setit! (:resource this) (:value op))
               (assoc op :type :ok))))

  (teardown! [this test]
    (.close client)))

(defn register-client
  "A basic CAS register on top of a single key and bin."
  [nodes]
  (RegisterClient. nil nodes))

; Generators

(defn r   [_ _] {:type :invoke, :f :read})
(defn add [_ _] {:type :invoke, :f :add, :value 1})

(defn std-gen
  "Takes a client generator and wraps it in a typical schedule and nemesis causing failover."
  [gen]
  (gen/phases
    (->> gen
         (gen/nemesis
           (gen/seq (cycle [(gen/sleep 2)
                            {:type :info :f :start}
                            (gen/sleep 10)
                            {:type :info :f :stop}])))
         (gen/time-limit 60))
    ; Recover
    (gen/nemesis
      (gen/once {:type :info :f :stop}))
    ; Wait for resumption of normal ops
    (gen/clients
      (->> gen
           (gen/time-limit 5)))))

; Tests

(defn copycat-test
  "Returns a map of base test settings"
  [name]
  (let [base-test tests/noop-test
        node-set (into #{} (:nodes base-test))]
    (merge base-test
           {:name     (str "copycat " name)
            :os       debian/os
            :db       (db "1.0" node-set)
            :nemesis  (nemesis/partition-random-halves)
            :ssh      {:private-key-path "/home/vagrant/.ssh/id_rsa"}
            :node-set node-set})))

(defn counter-test
  "Returns a map of jepsen test configuration for testing a counter"
  []
  (let [base-test (copycat-test "counter")]
    (merge base-test
           {:client    (register-client (:node-set base-test))
            :generator (->> (repeat 100 add)
                            (cons r)
                            gen/mix
                            (gen/delay 1/100)
                            std-gen)
            :checker   (checker/compose {:counter checker/counter
                                         :latency (checker/latency-graph "report/")})})))