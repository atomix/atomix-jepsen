(ns atomix-jepsen.core
    (:require [clojure [pprint :refer :all]
               [string :as str]]
              [clojure.java.io :as io]
              [clojure.tools.logging :refer [debug info warn error]]
              [atomix-jepsen.util :as cutil]
              [trinity.core :as trinity]
              [jepsen [core :as jepsen]
               [db :as db]
               [util :as util :refer [meh timeout]]
               [control :as c :refer [|]]
               [client :as client]
               [checker :as checker]
               [model :as model]
               [generator :as gen]
               [nemesis :as nemesis]
               [store :as store]
               [report :as report]
               [tests :as tests]]
              [jepsen.control [net :as net]
               [util :as net/util]]
              [jepsen.os.debian :as debian]
              [jepsen.checker.timeline :as timeline])

  ;(:import (java.util.concurrent ExecutionException)
  ;         (net.kuujo.copycat Copycat CopycatClient CopycatReplica))
  )

(defn- node-id [node]
  (Integer/parseInt (subs (name node) 1)))

; Test lifecycle

(defn install!
  "Installs atomix on the given node."
  [node version]

  ; Setup for non atomix-jepsen Docker environments
  (when (nil? (debian/installed-version "oracle-java8-installer"))
    (info node "installing JDK")
    (c/su
      (c/exec :echo (c/lit "\"deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main\" | tee /etc/apt/sources.list.d/webupd8team-java.list"))
      (c/exec :echo (c/lit "\"deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main\" | tee -a /etc/apt/sources.list.d/webupd8team-java.list"))
      (c/exec :echo (c/lit "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections"))
      (c/exec :apt-key (c/lit "adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886")))
    (debian/update!)
    (debian/install ["oracle-java8-installer" "oracle-java8-set-default" "git" "maven"]))

  ; Install atomix
  (when (not (get (System/getenv) "DEV"))
    (c/su
      (info node "fetching atomix")
      (if-not (cutil/dir-exists "/opt/atomix")
        (c/cd "/opt"
              (c/exec :git :clone "https://github.com/atomix/atomix.git"))
        (c/cd "/opt/atomix"
              (c/exec :git :pull)))
      (info node "building atomix")
      (c/cd "/opt/atomix"
            (c/exec :git :checkout)
            (c/exec :mvn :clean :install "-DskipTests=true"))
      (c/cd "/opt/atomix/examples"
            (c/exec :mvn :clean :install)))))

(defn start!
  "Starts atomix."
  [node test]
  (let [nodes (:node-set test)
        other-nodes (apply merge
                           (map #(assoc {} % (disj nodes %))
                                nodes))
        local-node-arg "5555"
        other-node-args (map #(str (name %) ":5555")
                             (node other-nodes))
        jarfile "/root/.m2/repository/io/atomix/atomix-server-example/0.1.0-SNAPSHOT/atomix-server-example-0.1.0-SNAPSHOT-shaded.jar"]
    (info node "starting atomix")
    (meh (c/exec :truncate :--size 0 "/var/log/atomix.log"))
    (c/su
      (meh (c/exec :rm :-rf "/root/logs/"))
      (c/cd "/root"
            (c/exec :java :-jar jarfile local-node-arg other-node-args
                    (c/lit "2>> /dev/null >> /var/log/atomix.log & echo $!"))))))

(defn stop!
  "Stops atomix."
  [node]
  (info node "stopping atomix")
  (c/su
    (meh (c/exec :pkill :-9 :java))))

(defn db
  "Atomix database"
  [version]
  (reify db/DB
    (setup! [this test node]
      (doto node
        (install! version)
        (start! test)))

    (teardown! [this test node]
      (stop! node))))

; Test clients

(defrecord CasRegisterClient [client register nodes]
  client/Client
  (setup! [this test node]
    (let [node-set (map #(hash-map :id (node-id %)
                                   :host (name %)
                                   :port 5555)
                        nodes)]
      (cutil/try-until-success
        #(do
          (info "Creating client connection to" node-set)
          (let [client (trinity/client node-set)]
            (debug node "Client connected!")
            (assoc this :client client)
            (assoc this :register (trinity/dist-atom client "register"))))
        #(do
          (debug node "Connection attempt failed. Retrying..." %)
          (Thread/sleep 2000)))))

  (invoke! [this test op]
    (case (:f op)
      :read (assoc op
              :type :ok,
              :value (trinity/get register))

      :write (do
               (trinity/set! register (:value op))
               (assoc op :type :ok))

      :cas (let [[v v'] (:value op)
                 ok? (trinity/cas! register v v')]
             (assoc op :type (if ok? :ok :fail)))))

  (teardown! [this test]
    (trinity/close! client)))

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

; Nemesis

(def crash-nemesis
  "A nemesis that crashes a random subset of nodes."
  (nemesis/node-start-stopper
    cutil/mostly-small-nonempty-subset
    (fn start [test node] (stop! node) [:killed node])
    (fn stop [test node] (start! node test) [:restarted node])))

; Tests

(defn- base-test
  "Returns a map of base test settings"
  [name]
  (let [base-test tests/noop-test
        node-set (into #{} (:nodes base-test))]
    (merge base-test
           {:name     (str "atomix " name)
            :os       debian/os
            :db       (db "1.0")
            :checker  (checker/compose {:linear checker/linearizable})
            :node-set node-set})))

(defn- cas-register-test
  "Returns a map of jepsen test configuration for testing cas"
  [name opts]
  (let [base-test (base-test (str "cas register " name))]
    (merge base-test
           {:client    (cas-register-client (:node-set base-test))
            :model     (model/cas-register)
            :checker   (checker/compose {:linear checker/linearizable
                                         :latency (checker/latency-graph)})
            :generator (->> gen/cas
                            (gen/delay 1/2)
                            std-gen)}
           opts)))

(def cas-bridge-test
  (cas-register-test "bridge"
                     {:nemesis (nemesis/partitioner (comp nemesis/bridge shuffle))}))

(def cas-halves-test
  (cas-register-test "halves"
                     {:nemesis (nemesis/partition-random-halves)}))

(def cas-isolate-node-test
  (cas-register-test "isolate node"
                     {:nemesis (nemesis/partition-random-node)}))

(def cas-crash-subset-test
  (cas-register-test "crash"
                     {:nemesis crash-nemesis}))

;(def cas-leave-join-test
;  (cas-register-test "leave-join"
;                     {:nemesis crash-nemesis}))