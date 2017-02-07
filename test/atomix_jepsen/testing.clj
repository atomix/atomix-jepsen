(ns atomix-jepsen.testing
  (:require [clojure.test :refer :all]
            [clojure.stacktrace :refer :all]
            [atomix-jepsen.core :as core]
            [jepsen.core :as jepsen]
            [clojure.set :as set])
  (:import (java.util.concurrent TimeUnit Executors)
           (java.util Arrays)
           (java.lang.management ManagementFactory)))

(defn dump-threads []
  (println (Arrays/toString (.dumpAllThreads
                              (ManagementFactory/getThreadMXBean) false false))))

;(.scheduleAtFixedRate (Executors/newSingleThreadScheduledExecutor) dump-threads 0 5 TimeUnit/SECONDS)

(defn run-test!
  [test]
  (reset! core/active-nodes (set/difference (set (:nodes test))
                                            (:inactive-nodes test)))
  (reset! core/inactive-nodes (:inactive-nodes test))
  (try
    (let [clean-test (dissoc test :active-nodes :inactive-nodes)
          test (jepsen/run! clean-test)]
      (is (:valid? (:results test))))
    (catch Exception e
      (print-stack-trace e)
      (throw e))))