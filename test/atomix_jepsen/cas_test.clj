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

; Steady cluster state tests

(deftest bridge
  (run-test! cas-bridge-test))

(deftest isolate-node
  (run-test! cas-isolate-node-test))

(deftest random-halves
  (run-test! cas-random-halves-test))

(deftest majorities-ring
  (run-test! cas-majorities-ring-test))

(deftest crash-subset
  (run-test! cas-crash-subset-test))

;(deftest compact
;  (run-test! cas-compact-test))

(deftest clock-drift
  (run-test! cas-clock-drift-test))

; Bootstrapping cluster tests

(deftest bootstrap
  (run-test! cas-bootstrap-test))

(deftest bridge-bootstrap
  (run-test! cas-bridge-bootstrap-test))

(deftest isolate-node-bootstrap
  (run-test! cas-isolate-node-bootstrap-test))

(deftest random-halves-bootstrap
  (run-test! cas-random-halves-bootstrap-test))

(deftest majorities-ring-bootstrap
  (run-test! cas-majorities-ring-bootstrap-test))

(deftest crash-subset-bootstrap
  (run-test! cas-crash-subset-bootstrap-test))

(deftest clock-drift-bootstrap
  (run-test! cas-clock-drift-bootstrap-test))