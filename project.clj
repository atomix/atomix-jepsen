(defproject copycat-jepsen "0.1.0-SNAPSHOT"
  :description "Copycat Jepsen tests"
  :url "http://github.com/kuujo/copycat"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [jepsen "0.0.4-SNAPSHOT"]
                 [net.kuujo.copycat/copycat "0.6.0-SNAPSHOT"]
                 [net.kuujo.copycat/copycat-netty "0.6.0-SNAPSHOT"]
                 [net.kuujo.copycat/copycat-atomic "0.6.0-SNAPSHOT"]])
