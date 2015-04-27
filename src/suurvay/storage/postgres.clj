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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Automatically create SQL arrays from vectors
(extend-protocol clojure.java.jdbc/ISQLParameter
  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt i v)))))

(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  java.sql.Array
  (result-set-read-column [val _ _]
    (into [] (.getArray val))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-followers!
  [db id followers]
  {:pre [(coll? followers)]}
  (jdbc/query db ["select add_followers(?, ?::bigint[])"
                  id, followers])
  nil)

(defn add-friends!
  [db id friends]
  {:pre [(coll? friends)]}
  (jdbc/query db ["select add_friends(?, ?::bigint[])"
                  id, friends])
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

(defn get-followers*
  [db id]
  (->> {:select [:id]
        :from [:follows]
        :where [:= :follows id]}
    sql/format
    (jdbc/query db)
    (map :id)))

(defn get-friends*
  [db id]
  (->> {:select [:follows]
        :from [:follows]
        :where [:= :id id]}
    sql/format
    (jdbc/query db)
    (map :follows)))

(defn pg-storage
  [db-spec]
  ;; TODO: validate db-spec here
  (reify Storage
    (store-user [_ user] (insert-user! db-spec user))
    (store-status [_ status] (insert-status! db-spec status))
    (store-timeline [_ timeline] (insert-statuses! db-spec timeline))
    (store-followers [_ id followers] (add-followers! db-spec id followers))
    (store-friends [_ id friends] (add-friends! db-spec id friends))

    (get-user [_ id] (get-user* db-spec id))
    (get-status [_ id] (get-status* db-spec id))
    (get-timeline [_ id] (get-timeline* db-spec id))
    (get-followers [_ id] (get-followers* db-spec id))
    (get-friends [_ id] (get-friends* db-spec id))))
