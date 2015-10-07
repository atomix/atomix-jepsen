(ns atomix-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer [error]]
            [atomix-jepsen.core :refer :all]
            [jepsen [core :as jepsen]
             [report :as report]]))

;(deftest cas-register
;  ;loop []
;  (let [test  (jepsen/run! (cas-register-test))
;        valid (:valid? (:results test))]
;
;    (do
;      (is valid)
;      (report/to "report/history.txt" (pprint (:history test)))
;      (report/to "report/linearizability.txt"
;                 (-> test :results :linear report/linearizability))))
;  ;(recur)
;  )

(defn current-time
  []
  (->> (java.util.Date.)
       (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss"))))

(defn- run-cas-register-test!
  "Runs a cas register test"
  [test]
  (try
    (let [test (jepsen/run! test)
          timestamp (current-time)]
      (or (is (:valid? (:results test)))
          (println (:error (:results test))))
      (report/to (str "report/" timestamp "/" (:name test) "-history.edn")
                 (pprint (:history test)))
      (report/to (str "report/" timestamp "/" (:name test) "-linearizability.txt")
                 (-> test :results :linear report/linearizability)))
    (catch Exception e
      (error e "Unexpected exception"))))

(deftest cas-test-bridge
  (run-cas-register-test! cas-bridge-test))

(deftest cas-test-isolate-node
  (run-cas-register-test! cas-isolate-node-test))

(deftest cas-test-halves
  (run-cas-register-test! cas-halves-test))

(deftest cas-test-crash-subset
  (run-cas-register-test! cas-crash-subset-test))