(ns copycat-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [copycat-jepsen.core :refer :all]
            [jepsen [core :as jepsen]
             [report :as report]]))

(deftest counter
  (let [test (jepsen/run! (counter-test))]
    (is (:valid? (:results test)))
    (report/to "report/counter.txt"
               (-> test :results :counter pprint))))
