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
  [thunk failure-thunk]
  (loop []
    (if-let [result (try
                      (thunk)
                      (catch Exception e
                        (failure-thunk e)
                        nil))]
      result
      (recur))))

(defn mostly-small-nonempty-subset
  "Returns a subset of the given collection, with a logarithmically decreasing
  probability of selecting more elements. Always selects at least one element.
      (->> #(mostly-small-nonempty-subset [1 2 3 4 5])
           repeatedly
           (map count)
           (take 10000)
           frequencies
           sort)
      ; => ([1 3824] [2 2340] [3 1595] [4 1266] [5 975])"
  [bootstrap decomission]
  (fn [xs]
    (-> xs
        count
        inc
        Math/log
        rand
        Math/exp
        long
        (take (shuffle xs))
        set
        (set/difference @bootstrap)
        set
        (set/difference @decomission)
        shuffle)))