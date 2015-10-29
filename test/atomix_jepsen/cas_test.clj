(ns atomix-jepsen.cas-test
  (:require [clojure.test :refer :all]
            [clojure.stacktrace :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer [error]]
            [atomix-jepsen.core :as core]
            [atomix-jepsen.cas :refer :all]
            [jepsen [core :as jepsen]]))

(defn- run-test!
  [test]
  (reset! core/bootstrap (:bootstrap test))
  (reset! core/decommission (:decomission test))
  (try
    (let [clean-test (dissoc test :bootstrap :decomission)
          test (jepsen/run! clean-test)]
      (is (:valid? (:results test))))
    (catch Exception e
      (print-stack-trace e)
      (throw e))))

(deftest cas-test-bootstrap
  (run-test! cas-bootstrap-test))

(deftest cas-test-bridge
  (run-test! cas-bridge-test))

(deftest cas-test-isolate-node
  (run-test! cas-isolate-node-test))

(deftest cas-test-halves
  (run-test! cas-halves-test))

(deftest cas-test-crash-subset
  (run-test! cas-crash-subset-test))

;(deftest cas-test-clock-drift
;  (run-test! cas-clock-drift-test))