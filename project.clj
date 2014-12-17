(defproject suurvay "0.1.0-SNAPSHOT"
  :description "A framework for building Twitter autoblocker bots."
  :url "https://github.com/holguinj/suurvay"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
 ;                [twitter-schemas "0.1.0-SNAPSHOT"]
                 [environ "1.0.0"]
                 [prismatic/schema "0.3.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [caribou/butterfly "0.1.3"]
                 [twitter-api "0.7.7-DEBUG"]])
