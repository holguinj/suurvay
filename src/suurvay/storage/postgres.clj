(ns suurvay.storage.postgres
  (:require [clojure.java.jdbc :as jdbc]
            [cheshire.core :as json]
            [honeysql.core :as sql]
            [suurvay.storage :refer [Storage]]))

(def test-db
  {:subprotocol "postgresql"
   :subname "suurvay_test"
   :user "suurvay_test"
   :password "suurvay_test"})

(def pgobject->json (comp #(json/decode % keyword) str))

(defn insert-user!
  [db user]
  (jdbc/query db ["select insert_user(?::json)"
                  (json/encode user)])
  nil)

(defn insert-statuses!
  [db statuses]
  {:pre [(coll? statuses)
         (every? map? statuses)]}
  (jdbc/query db ["select insert_statuses(?::json)"
                  (json/encode statuses)])
  nil)

(defn insert-status!
  [db status]
  (insert-statuses! db [status]))

(defn get-user*
  [db id]
  (->> {:select [:user_object]
        :from [:twitter_users]
        :limit 1
        :where [:= :twitter_users.id id]}
    sql/format
    (jdbc/query db)
    first
    :user_object
    pgobject->json))

(defn get-status*
  [db id]
  (->> {:select [:status]
        :from [:statuses]
        :limit 1
        :where [:= :statuses.id id]}
    sql/format
    (jdbc/query db)
    first
    :status
    pgobject->json))

(defn get-timeline*
  [db id]
  (->> {:select [:status]
        :from [:statuses]
        :where [:= :statuses.user_id id]}
    sql/format
    (jdbc/query db)
    (map (comp pgobject->json :status))))

(defn pg-storage
  [db-spec]
  ;; TODO: validate db-spec here
  (reify Storage
    (store-user [_ user] (insert-user! db-spec user))
    (store-status [_ status] (insert-status! db-spec status))
    (store-timeline [_ timeline] (insert-statuses! db-spec timeline))

    (get-user [_ id] (get-user* db-spec id))
    (get-status [_ id] (get-status* db-spec id))
    (get-timeline [_ id] (get-timeline* db-spec id))))
