(ns atomix-jepsen.core
  (:require [clojure [pprint :refer :all]]
            [clojure.tools.logging :refer [debug info warn error]]
            [atomix-jepsen
             [util :as cutil]]
            [jepsen
             [db :as db]
             [util :as util :refer [meh timeout]]
             [control :as c :refer [|]]
             [client :as client]
             [generator :as gen]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.os.debian :as debian]
            [clojure.set :as set]))

; Vars

(def active-nodes (atom #{}))
(def inactive-nodes (atom #{}))

; Test lifecycle

(defn install!
  "Installs atomix on the given node."
  [node]

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

  ; Install atomix-jepsen replica
  (when (not (get (System/getenv) "DEV"))
    (c/su
      (info node "fetching atomix-jepsen")
      (if-not (cutil/dir-exists "/opt/atomix-jepsen")
        (c/cd "/opt"
              (c/exec :git :clone "https://github.com/atomix/atomix-jepsen.git"))
        (c/cd "/opt/atomix-jepsen"
              (c/exec :git :pull)))
      (info node "building atomix-jepsen replica")
      (c/cd "/opt/atomix-jepsen"
            (c/exec :git :checkout)
            (c/exec :lein :clean)
            (c/exec :lein :uberjar)
            (c/exec :lein :localrepo :install "target/atomix-replica.jar" "io.atomix.atomix-jepsen/replica" "0.1.0")))))

(defn start!
  "Starts atomix replica."
  [node test]
  (let [local-port "5555"
        members (doall (map #(str (name %) ":5555")
                                 @active-nodes))
        jarfile "/root/.m2/repository/io/atomix/atomix-jepsen/replica/0.1.0/replica-0.1.0.jar"]
    (info node "starting atomix replica")
    (meh (c/exec :truncate :--size 0 "/var/log/atomix.log"))
    (c/su
      (meh (c/exec :rm :-rf "/root/logs/"))
      (c/cd "/root"
            (c/exec :java :-jar jarfile "/root/logs/" local-port members
                    (c/lit "2>> /dev/null >> /var/log/atomix.log & echo $!"))))))

(defn guarded-start!
  "Guarded start that only starts active nodes."
  [node test]
  (when (node @active-nodes)
    (start! node test)))

(defn stop!
  "Stops atomix."
  [node]
  (info node "stopping atomix")
  (c/su
    (meh (c/exec :pkill :-9 :java))))

(defn db
  "Atomix database"
  []
  (reify db/DB
    (setup! [this test node]
      (doto node
        (install!)
        (guarded-start! test)))

    (teardown! [this test node]
      (stop! node))))

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

(defn nemesis-gen
  [pause-before-start pause-before-stop src-gen]
  (gen/nemesis
    (gen/seq (cycle [(gen/sleep pause-before-start)
                     {:type :info :f :start}
                     (gen/sleep pause-before-stop)
                     {:type :info :f :stop}]))
    src-gen))

(defn std-gen
  "Takes a client generator and wraps it in a typical schedule and nemesis causing failover."
  [gen]
  (gen/phases
    (->> gen
         (nemesis-gen 8 8)
         (gen/time-limit 20))
    (recover)
    (read-once)))

; Nemesis

;(defn crash-nemesis
;  "A nemesis that crashes a random subset of nodes."
;  []
;  (test-aware-node-start-stopper
;    safe-mostly-small-nonempty-subset
;    (fn start [test node] (stop! node) [:killed node])
;    (fn stop [test node] (start! node test) [:restarted node])))

(defn crash-nemesis
  "A nemesis that crashes a random subset of 1-3 nodes."
  []
  (let [nodes (atom nil)]
    (reify client/Client
      (setup! [this test _] this)

      (invoke! [this test op]
        (locking nodes
          (assoc op :type :info, :value
                    (case (:f op)
                      :start (let [max-nodes-to-kill (- (count nodes) 2)
                                   target-nodes (take (inc (rand-int max-nodes-to-kill))
                                                      (shuffle nodes))]
                               (reset! nodes target-nodes)
                               (doseq [node target-nodes]
                                 (stop! node))
                               :no-target)
                      :stop (do
                              (reset! nodes nil)
                              (doseq [node @nodes]
                                (start! node test))
                              :not-started)))))

      (teardown! [this test]))))

(defn config-change-nemesis
  "Changes cluster config by adding or removing nodes."
  []
  (reify client/Client
    (setup! [this test _] this)

    (invoke! [this test op]
      (assoc op :type :info, :value
                (case (:f op)
                  :start (let [directive (case (count @inactive-nodes)
                                           0 :leave
                                           1 (if (= 1 (rand-int 2))
                                               :leave
                                               :join)
                                           2 :join)]
                           (case directive
                             :leave (let [node (first (shuffle @active-nodes))]
                                      (swap! inactive-nodes conj node)
                                      (swap! active-nodes disj node)
                                      (info node " leaving")
                                      (c/on node (stop! node)))
                             :join (let [node (first (shuffle @inactive-nodes))]
                                     (swap! inactive-nodes disj node)
                                     (swap! active-nodes conj node)
                                     (info node " joining")
                                     (c/on node (start! node test))))
                           :no-target)
                  :stop :no-target)))

    (teardown! [this test])))

(defn combine-nemesis
  "Combines a pair of nemesis(es?), executing both and returning results from nemesis2"
  [nemesis1 nemesis2]
  (reify client/Client
    (setup! [this test node]
      (client/setup! nemesis1 test node)
      (client/setup! nemesis2 test node))

    (invoke! [this test op]
      (client/invoke! nemesis1 test op)
      (client/invoke! nemesis2 test op))

    (teardown! [this test]
      (client/teardown! nemesis1 test)
      (client/teardown! nemesis2 test))))

; Tests

(defn atomix-test
  "Returns a map of atomix test settings"
  [name opts]
  (merge tests/noop-test
         {:name           (str "atomix " name)
          :os             debian/os
          :db             (db)
          :inactive-nodes #{}
          :active-nodes   #{}}
         opts))