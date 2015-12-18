(ns atomix-jepsen.dvalue-test
  (:require [clojure.test :refer :all]
            [clojure.stacktrace :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer [error]]
            [atomix-jepsen.dvalue :refer :all]
            [atomix-jepsen.testing :refer :all]))

; Baseline tests

(deftest bridge
  (run-test! dvalue-bridge-test))

(deftest isolate-node
  (run-test! dvalue-isolate-node-test))

(deftest random-halves
  (run-test! dvalue-random-halves-test))

(deftest majorities-ring
  (run-test! dvalue-majorities-ring-test))

(deftest crash-subset
  (run-test! dvalue-crash-subset-test))

;(deftest compact
;  (run-test! dvalue-compact-test))

(deftest clock-drift
  (run-test! dvalue-clock-drift-test))

; Cluster config change tests

(deftest join
  (run-test! dvalue-config-change-test))

(deftest bridge-join
  (run-test! dvalue-bridge-config-change-test))

(deftest isolate-node-join
  (run-test! dvalue-isolate-node-config-change-test))

(deftest random-halves-join
  (run-test! dvalue-random-halves-config-change-test))

(deftest majorities-ring-join
  (run-test! dvalue-majorities-ring-config-change-test))

(deftest crash-subset-join
  (run-test! dvalue-crash-subset-config-change-test))

(deftest clock-drift-join
  (run-test! dvalue-clock-drift-config-change-test))