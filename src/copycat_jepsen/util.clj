(ns copycat-jepsen.util
    (:require [jepsen [control :as c]
               [util :as util]]))

(defn dir-exists
  [path]
  (= "1" (util/meh
         (c/exec
           (c/lit (str "test -d " path " && echo 1"))))))