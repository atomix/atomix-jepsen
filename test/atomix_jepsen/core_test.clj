(ns atomix-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer [error]]
            [atomix-jepsen.core :refer :all]
            [jepsen [core :as jepsen]]))

(defn- run-test!
  [test]
  (let [test (jepsen/run! test)]
    (is (:valid? (:results test)))))

(deftest cas-test-bridge
  (run-test! cas-bridge-test))

(deftest cas-test-isolate-node
  (run-test! cas-isolate-node-test))

(deftest cas-test-halves
  (run-test! cas-halves-test))

(deftest cas-test-crash-subset
  (run-test! cas-crash-subset-test))

(deftest cas-test-clock-drift
  (run-test! cas-clock-drift-test))