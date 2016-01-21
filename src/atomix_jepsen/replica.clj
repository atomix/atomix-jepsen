(ns atomix-jepsen.replica
  "An Atomix replica to run on Jepsen nodes"
  (:gen-class)
  (:require [trinity
             [core :as trinity]]
            [clojure.string :as str])
  (:import (io.atomix.catalyst.transport NettyTransport)
           (io.atomix.copycat.server.storage Storage)
           (io.atomix AtomixReplica)))

(defn -main [& args]
  (when (< (count args) 3)
    (println "Must supply a storage path, local port, and cluster member host:port pairs.")
    (System/exit -1))

  (let [[storage-path local-port & host-ports] args
        hosts (map #(let [hp (str/split % #":")]
                            {:host (first hp) :port (Integer/parseInt (last hp))})
                          host-ports)
        storage (Storage. ^String storage-path)
        transport (NettyTransport.)
        options {:storage storage :transport transport}
        ^AtomixReplica replica (trinity/replica (Integer/parseInt local-port) hosts options)]

    (trinity/open! replica)
    (while (.isOpen replica)
      (Thread/sleep 1000))))