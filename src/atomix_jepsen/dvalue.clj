(ns atomix-jepsen.dvalue
  ; Tests the linearizability of an Atomix DistributedValue
  (:require [clojure [pprint :refer :all]
             [set :as set]]
            [clojure.tools.logging :refer [debug info warn error]]
            [atomix-jepsen
             [core :refer :all]
             [util :as cutil]]
            [trinity
             [core :as trinity]
             [distributed-value :as dvalue]]
            [jepsen
             [util :as util :refer [meh timeout]]
             [control :as c :refer [|]]
             [client :as client]
             [checker :as checker]
             [model :as model]
             [generator :as gen]
             [nemesis :as nemesis]])
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
            (let [atomix-client (-> (trinity/client node-set)
                                    (trinity/open!))
                  _ (debug "Client connected!")
                  test-name (:name test)
                  register (dvalue/create atomix-client test-name)]
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
                :value (dvalue/get register))

        :write (do
                 (dvalue/set! register (:value op))
                 (assoc op :type :ok))

        :cas (let [[v v'] (:value op)
                   ok? (dvalue/cas! register v v')]
               (assoc op :type (if ok? :ok :fail))))
      (catch ExecutionException e
        (assoc op :type :fail :value (.getMessage e)))))

  (teardown! [this test]
    (info "Closing client " client)
    (trinity/close! client)))

(defn dvalue-register-client
  "A basic CAS register client."
  []
  (CasRegisterClient. nil nil))

; Tests

(defn- dvalue-register-test
  "Returns a map of jepsen test configuration for testing cas"
  [name opts]
  (merge (atomix-test (str "cas register " name)
                      {:client    (dvalue-register-client)
                       :model     (model/cas-register)
                       :checker   (checker/compose {:linear  checker/linearizable
                                                    :latency (checker/latency-graph)})
                       :generator (->> gen/cas
                                       (gen/delay 1/2)
                                       std-gen)})
         opts))

; Baseline tests

(def combined-nemesis-test
  (dvalue-register-test "combined nemesis"
                     {:nemesis   (nemesis/partitioner (comp nemesis/bridge shuffle))
                      :generator (->> gen/cas
                                      (gen/delay 1/2)
                                      std-gen)}))

(def dvalue-bridge-test
  (dvalue-register-test "bridge"
                     {;:nemesis (nemesis/partitioner (comp nemesis/bridge shuffle))
                     }))

(def dvalue-isolate-node-test
  (dvalue-register-test "isolate node"
                     {:nemesis (nemesis/partition-random-node)}))

(def dvalue-random-halves-test
  (dvalue-register-test "random halves"
                     {:nemesis (nemesis/partition-random-halves)}))

(def dvalue-majorities-ring-test
  (dvalue-register-test "majorities ring"
                     {:nemesis (nemesis/partition-majorities-ring)}))

(def dvalue-crash-subset-test
  (dvalue-register-test "crash"
                     {:nemesis (crash-nemesis)}))

;(def dvalue-compact-test
;  (dvalue-register-test "compact"
;                     {:nemesis (compact-nemesis)}))

(def dvalue-clock-drift-test
  (dvalue-register-test "clock drift"
                     {:nemesis (nemesis/clock-scrambler 10000)}))

; Configuration change tests

(def dvalue-config-change-test
  (dvalue-register-test "join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (config-change-nemesis)}))

(def dvalue-bridge-config-change-test
  (dvalue-register-test "bridge join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (comp nemesis/partitioner (comp nemesis/bridge shuffle)))}))

(def dvalue-random-halves-config-change-test
  (dvalue-register-test "random halves join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (nemesis/partition-random-halves))}))

(def dvalue-isolate-node-config-change-test
  (dvalue-register-test "isolate node join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (nemesis/partition-random-node))}))

(def dvalue-majorities-ring-config-change-test
  (dvalue-register-test "majorities ring join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (nemesis/partition-majorities-ring))}))

(def dvalue-crash-subset-config-change-test
  (dvalue-register-test "crash join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (crash-nemesis))}))

(def dvalue-clock-drift-config-change-test
  (dvalue-register-test "clock drift join"
                     {:inactive-nodes #{:n4 :n5}
                      :nemesis        (combine-nemesis (config-change-nemesis)
                                                       (nemesis/clock-scrambler 10000))}))
