(ns atomix-jepsen.dmap-test
  (:require [clojure.test :refer :all]
            [clojure.stacktrace :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer [error]]
            [atomix-jepsen.dvalue :refer :all]
            [atomix-jepsen.testing :refer :all]))

; Baseline tests

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

; Cluster config change tests

(deftest join
  (run-test! cas-config-change-test))

(deftest bridge-join
  (run-test! cas-bridge-config-change-test))

(deftest isolate-node-join
  (run-test! cas-isolate-node-config-change-test))

(deftest random-halves-join
  (run-test! cas-random-halves-config-change-test))

(deftest majorities-ring-join
  (run-test! cas-majorities-ring-config-change-test))

(deftest crash-subset-join
  (run-test! cas-crash-subset-config-change-test))

(deftest clock-drift-join
  (run-test! cas-clock-drift-config-change-test))