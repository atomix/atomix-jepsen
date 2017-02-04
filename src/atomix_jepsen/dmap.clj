(ns atomix-jepsen.dmap
  ; Tests the linearizability of an Atomix DistributedMap
  (:require [clojure [pprint :refer :all]]
            [clojure.tools.logging :refer [debug info warn error]]
            [atomix-jepsen
             [core :refer :all]
             [util :as cutil]]
            [trinity
             [core :as trinity]
             [distributed-map :as dmap]]
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

(defrecord DMapClient [client dmap]
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
                                    (trinity/connect!))
                  _ (debug "Client connected!")
                  test-name (:name test)
                  dmap (trinity/get-map atomix-client test-name)]
              (debug "Created atomix resource" test-name)
              (assoc this :client atomix-client
                          :dmap dmap)))
          #(do
            (debug "Connection attempt failed. Retrying..." %)
            (Thread/sleep 2000))))))

  (invoke! [this test op]
    (try
      (case (:f op)
        :read (assoc op
                :type :ok,
                :value (dmap/get dmap "test"))

        :write (do
                 (dmap/put! dmap "test" (:value op))
                 (assoc op :type :ok)))
      (catch ExecutionException e
        (assoc op :type :fail :value (.getMessage e)))))

  (teardown! [this test]
    (info "Closing client " client)
    (trinity/close! client)))

(defn dmap-client
  "A basic Map client."
  []
  (DMapClient. nil nil))

; Tests

(defn- map-test
  "Returns a map of jepsen test configuration for testing a distributed map"
  [name opts]
  (merge (atomix-test (str "map " name)
                      {:client    (dmap-client)
                       :model     (model/set)
                       :checker   (checker/compose {:linear  checker/linearizable
                                                    :latency (checker/latency-graph)})
                       :generator std-gen})
         opts))

; Baseline tests
