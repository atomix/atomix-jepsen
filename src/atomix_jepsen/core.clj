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
            [jepsen.os.debian :as debian]))

; Vars

(def bootstrap (atom #{}))
(def decommission (atom #{}))

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
  (let [nodes (set (:nodes test))
        other-nodes (clojure.set/difference nodes (set [node]))
        local-node-arg "5555"
        other-node-args (doall (map #(str (name %) ":5555")
                                    other-nodes))
        jarfile "/root/.m2/repository/io/atomix/atomix-standalone-server-example/0.1.0-SNAPSHOT/atomix-standalone-server-example-0.1.0-SNAPSHOT-shaded.jar"]
    (info node "starting atomix")
    (meh (c/exec :truncate :--size 0 "/var/log/atomix.log"))
    (c/su
      (meh (c/exec :rm :-rf "/root/logs/"))
      (c/cd "/root"
            (c/exec :java :-jar jarfile local-node-arg other-node-args
                    (c/lit "2>> /dev/null >> /var/log/atomix.log & echo $!"))))))

(defn guarded-start!
  "Guarded start that only starts non bootstrap and decommission nodes."
  [node test]
  (when-not (or (node @bootstrap) (node @decommission))
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
         (gen/time-limit 60))
    (recover)
    (read-once)))

; Nemesis

(defn crash-nemesis
  "A nemesis that crashes a random subset of nodes."
  []
  (nemesis/node-start-stopper
    (cutil/mostly-small-nonempty-subset bootstrap decommission)
    (fn start [test node] (stop! node) [:killed node])
    (fn stop [test node] (start! node test) [:restarted node])))

(defn bootstrap-nemesis
  "Bootstraps a new node into a running atomix cluster"
  []
  (reify client/Client
    (setup! [this test _] this)

    (invoke! [this test op]
      (assoc op :type :info, :value
                (case (:f op)
                  :start (if-let [node (first @bootstrap)]
                           (do (swap! bootstrap rest)
                               (info "bootstrapping " node)
                               (c/on node (start! node test))))
                  :no-target
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
         {:name         (str "atomix " name)
          :os           debian/os
          :db           (db)
          :bootstrap    #{}
          :decommission #{}}
         opts))
