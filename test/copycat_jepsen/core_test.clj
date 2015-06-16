(ns copycat-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [copycat-jepsen.core :refer :all]
            [jepsen [core :as jepsen]
             [report :as report]]))

(deftest cas-register
  (loop []
    (let [test (jepsen/run! (cas-register-test))
          valid (:valid? (:results test))]
      (if-not valid
        (do
          (is valid)
          (report/to "report/history.txt" (pprint (:history test)))
          (report/to "report/linearizability.txt"
                     (-> test :results :linear report/linearizability)))
        (recur)))))