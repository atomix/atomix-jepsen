(ns copycat-jepsen.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [copycat-jepsen.core :refer :all]
            [jepsen [core :as jepsen]
             [report :as report]]))

(deftest cas
  (let [test (jepsen/run! (cas-test))]
    (is (:valid? (:results test)))
    (report/to "report/cas.txt"
               (-> test :results :cas pprint))))
