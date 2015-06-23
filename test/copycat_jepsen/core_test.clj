(ns copycat-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [copycat-jepsen.core :refer :all]
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

(defn date-tag
  "Date tagging for the report directory"
  []
  (->> (java.util.Date.)
       (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-ss"))))

(def timestamp (date-tag))

(defn- run-cas-register-test!
  "Runs a cas register test"
  [test ts]
  (let [test (jepsen/run! test)]
    (or (is (:valid? (:results test)))
        (println (:error (:results test))))
    (report/to (str "report-" ts "/" (:name test) "/history.edn")
               (pprint (:history test)))
    (report/to (str "report-" ts "/" (:name test) "/linearizability.txt")
               (-> test :results :linear report/linearizability))))

(deftest cas-test-bridge
  (run-cas-register-test! bridge-test timestamp))

(deftest cas-test-isolate-node
  (run-cas-register-test! isolate-node-test timestamp))

(deftest cas-test-halves
  (run-cas-register-test! halves-test timestamp))

(deftest cas-test-crash-subset
  (run-cas-register-test! crash-subset-test timestamp))