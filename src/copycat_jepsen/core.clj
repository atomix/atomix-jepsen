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
            [jepsen.checker.timeline :as timeline]
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
  [node test]
  (let [nodes (:node-set test)
        other-nodes (apply merge
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
                    (c/lit "2>> /dev/null >> /var/log/copycat.log & echo $!"))))))

(defn stop!
  "Stops copycat."
  [node]
  (info node "stopping copycat")
  (c/su
    (meh (c/exec :pkill :-9 :java))))

(defn db
  "Copycat database"
  [version]
  (reify db/DB
    (setup! [_ test node]
      (doto node
        (install! version)
        (start! test)))

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

(defn getit!
  "Gets a value from a reference"
  [^AsyncReference reference]
  (-> (.get reference) (.get)))

(defn setit!
  "Sets a value for a reference"
  [^AsyncReference reference value]
  (-> (.set reference value) (.get)))

(defn cas!
  "Compares and sets a value for a reference"
  [^AsyncReference reference expected updated]
  (-> (.compareAndSet reference expected updated) (.get)))

(defrecord CasRegisterClient [client resource nodes]
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
      :read (assoc op
              :type :ok,
              :value (getit! resource))

      :write (do
               (setit! resource (:value op))
               (assoc op
                 :type :ok))

      :cas   (let [[v v'] (:value op)
                   ok? (cas! resource v v')]
               (assoc op
                 :type (if ok? :ok :fail)))))

  (teardown! [this test]
    (.close client)))

(defn cas-register-client
  "A basic CAS register on top of a single key and bin."
  [nodes]
  (CasRegisterClient. nil nil nodes))

; Generators

(defn recover
  "A generator which stops the nemesis and allows some time for recovery."
  []
  (gen/nemesis
    (gen/concat
      (gen/log* "Recovering after Nemesis")
      (gen/once {:type :info, :f :stop}))))

(defn read-once
  "A generator which reads exactly once."
  []
  (gen/clients
    (gen/concat
      (gen/log* "Reading after Nemesis")
      (gen/once {:type :invoke, :f :read}))))

(defn std-gen
  "Takes a client generator and wraps it in a typical schedule and nemesis causing failover."
  [gen]
  (gen/phases
    (->> gen
         (gen/nemesis
           (gen/seq (cycle [(gen/sleep 5)
                            (gen/log* "Starting Nemesis")
                            {:type :info :f :start}
                            (gen/sleep 10)
                            (gen/log* "Stopping Nemesis")
                            {:type :info :f :stop}])))
         (gen/time-limit 60))
    (recover)
    (read-once)))

; Tests

(defn mostly-small-nonempty-subset
  "Returns a subset of the given collection, with a logarithmically decreasing
  probability of selecting more elements. Always selects at least one element.
      (->> #(mostly-small-nonempty-subset [1 2 3 4 5])
           repeatedly
           (map count)
           (take 10000)
           frequencies
           sort)
      ; => ([1 3824] [2 2340] [3 1595] [4 1266] [5 975])"
  [xs]
  (-> xs
      count
      inc
      Math/log
      rand
      Math/exp
      long
      (take (shuffle xs))))

(def crash-nemesis
  "A nemesis that crashes a random subset of nodes."
  (nemesis/node-start-stopper
    mostly-small-nonempty-subset
    (fn start [test node] (c/su (c/exec :killall :-9 :java)) [:killed node])
    (fn stop  [test node] (start! node test) [:restarted node])))

(defn- base-test
  "Returns a map of base test settings"
  [name]
  (let [base-test tests/noop-test
        node-set (into #{} (:nodes base-test))]
    (merge base-test
           {:name     (str "copycat " name)
            :os       debian/os
            :db       (db "1.0")
            :checker  (checker/compose {:linear checker/linearizable})
            :ssh      {:private-key-path "/home/vagrant/.ssh/id_rsa"}
            :node-set node-set})))

(defn- cas-register-test
  "Returns a map of jepsen test configuration for testing cas"
  [name opts]
  (let [base-test (base-test (str "cas register " name))]
    (merge base-test
           {:client    (cas-register-client (:node-set base-test))
            :model     (model/cas-register)
            :generator (->> gen/cas
                            (gen/delay 1/2)
                            std-gen)}
           opts)))

(def bridge-test
  (cas-register-test "bridge"
                     {:nemesis (nemesis/partitioner (comp nemesis/bridge shuffle))}))

(def halves-test
  (cas-register-test "halves"
                     {:nemesis (nemesis/partition-random-halves)}))

(def isolate-node-test
  (cas-register-test "isolate node"
                     {:nemesis (nemesis/partition-random-node)}))

(def crash-subset-test
  (cas-register-test "crash"
                     {:nemesis crash-nemesis}))