(ns atomix-jepsen.util
  (:require [jepsen [control :as c]
             [util :as util]]
            [clojure.set :as set]))

(defn dir-exists
  [path]
  (= "1" (util/meh
           (c/exec
             (c/lit (str "test -d " path " && echo 1"))))))

(defn try-until-success
  "Accepts a try-fn to try a failure-fn to call upon failure, supplying the failure/exception. The try-fn is retried
  until no failure is thrown."
  [try-fn failure-fn]
  (loop []
    (if-let [result (try
                      (try-fn)
                      (catch Exception e
                        (failure-fn e)
                        nil))]
      result
      (recur))))
