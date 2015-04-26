(defproject suurvay "0.2.0-SNAPSHOT"
  :description "A framework for building Twitter autoblocker bots."
  :url "https://github.com/holguinj/suurvay"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-selectors {:default (complement :limit)
                   :limit :limit
                   :all (constantly true)}
  :dependencies [[caribou/butterfly "0.1.3"]
                 [environ "1.0.0"]
                 [honeysql "0.5.2"]
                 [migratus "0.7.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.typed "0.2.72"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [postgresql "9.3-1102.jdbc41"]
                 [prismatic/schema "0.3.3"]
                 [twitter-api "0.7.8"]]
  :plugins [[migratus-lein "0.1.0"]]
  :migratus {:store :database
             :migration-dir "suurvay/migrations"
             :db {:classname "org.postgresql.Driver"
                  :subprotocol "postgresql"
                  :subname "//localhost:5432/suurvay_test"
                  :user "suurvay_test"
                  :password "suurvay_test"}})
