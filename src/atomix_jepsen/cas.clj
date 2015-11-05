(ns atomix-jepsen.cas
  (:require [clojure [pprint :refer :all]
             [string :as str]]
            [clojure.java.io :as io]
            [clojure.tools.logging :refer [debug info warn error]]
            [atomix-jepsen
             [core :refer :all]
             [util :as cutil]]
            [trinity.core :as trinity]
            [jepsen
             [core :as jepsen]
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
  (:import (java.util.concurrent ExecutionException)))

; Test clients

(def setup-lock (Object.))

(defrecord CasRegisterClient [client register]
  client/Client
  (setup! [this test node]
    ; One client connection at a time
    (locking setup-lock
      (let [node-set (map #(hash-map :host (name %)
                                     :port 5555)
                          (:nodes test))]
        (cutil/try-until-success
          #(do
            (info "Creating client connection to" node-set)
            (let [atomix-client (trinity/client node-set)
                  _ (debug "Client connected!")
                  test-name (:name test)
                  register (trinity/dist-atom atomix-client test-name)]
              (debug "Created atomix resource" test-name)
              (assoc this :client atomix-client
                          :register register)))
          #(do
            (debug "Connection attempt failed. Retrying..." %)
            (Thread/sleep 2000))))))

  (invoke! [this test op]
    (try
      (case (:f op)
        :read (assoc op
                :type :ok,
                :value (trinity/get register))

        :write (do
                 (trinity/set! register (:value op))
                 (assoc op :type :ok))

        :cas (let [[v v'] (:value op)
                   ok? (trinity/cas! register v v')]
               (assoc op :type (if ok? :ok :fail))))
      (catch ExecutionException e
        (assoc op :type :fail :value (.getMessage e)))))

  (teardown! [this test]
    (info "Closing client " client)
    (trinity/close! client)))

(defn cas-register-client
  "A basic CAS register client."
  []
  (CasRegisterClient. nil nil))

; Tests

(defn- cas-register-test
  "Returns a map of jepsen test configuration for testing cas"
  [name opts]
  (merge (atomix-test (str "cas register " name)
                      {:client    (cas-register-client)
                       :model     (model/cas-register)
                       :checker   (checker/compose {:linear  checker/linearizable
                                                    :latency (checker/latency-graph)})
                       :generator (->> gen/cas
                                       (gen/delay 1/2)
                                       std-gen)})
         opts))

; Baseline tests

(def cas-bridge-test
  (cas-register-test "bridge"
                     {:nemesis (nemesis/partitioner (comp nemesis/bridge shuffle))}))

(def cas-isolate-node-test
  (cas-register-test "isolate node"
                     {:nemesis (nemesis/partition-random-node)}))

(def cas-random-halves-test
  (cas-register-test "random halves"
                     {:nemesis (nemesis/partition-random-halves)}))

(def cas-majorities-ring-test
  (cas-register-test "majorities ring"
                     {:nemesis (nemesis/partition-majorities-ring)}))

(def cas-crash-subset-test
  (cas-register-test "crash"
                     {:nemesis (crash-nemesis)}))

;(def cas-compact-test
;  (cas-register-test "compact"
;                     {:nemesis (compact-nemesis)}))

(def cas-clock-drift-test
  (cas-register-test "clock drift"
                     {:nemesis (nemesis/clock-scrambler 10000)}))

; Bootstrap tests

;(def bridge-test-bootstrap
;  (cas-register-test "bridge bootstrap"
;                     {:bootstrap #{:n4 :n5}
;                      :nemesis   (bootstrap-nemesis (comp nemesis/partitioner (comp nemesis/bridge shuffle)))}))
;
;(def halves-test-bootstrap
;  (cas-register-test "halves bootstrap"
;                     {:bootstrap  (atom #{:n4 :n5})
;                      :conductors {:nemesis      (nemesis/partition-random-halves)
;                                   :bootstrapper (conductors/bootstrapper)}}))
;
;(def isolate-node-test-bootstrap
;  (cas-register-test "isolate node bootstrap"
;                     {:bootstrap  (atom #{:n4 :n5})
;                      :conductors {:nemesis      (nemesis/partition-random-node)
;                                   :bootstrapper (conductors/bootstrapper)}}))
;
;(def crash-subset-test-bootstrap
;  (cas-register-test "crash bootstrap"
;                     {:bootstrap  (atom #{:n4 :n5})
;                      :conductors {:nemesis      (crash-nemesis)
;                                   :bootstrapper (conductors/bootstrapper)}}))
;
;(def clock-drift-test-bootstrap
;  (cas-register-test "clock drift bootstrap"
;                     {:bootstrap  (atom #{:n4 :n5})
;                      :conductors {:nemesis      (nemesis/clock-scrambler 10000)
;                                   :bootstrapper (conductors/bootstrapper)}}))

(def cas-bootstrap-test
  (cas-register-test "bootstrap"
                     {:bootstrap #{:n4 :n5}
                      :nemesis   (bootstrap-nemesis)}))