(ns suurvay.storage.in-memory
  (:require [suurvay.storage :refer [Storage]]))

(def blank-storage-map
  {:users {}      ;; user-id -> user
   :statuses {}}) ;; status-id -> status

(defn store-user*
  [storage {:keys [id] :as user}]
  (swap! storage
         (fn [stg]
           (update-in stg [:users]
                      assoc id user)))
  nil)

(defn store-status*
  [storage {:keys [id] :as status}]
  (swap! storage
         (fn [stg]
           (update-in stg [:statuses]
                      assoc id status)))
  nil)

(defn store-timeline*
  [storage statuses]
  (let [new-statuses (->> (for [{:keys [id] :as status} statuses]
                            {id status})
                       (reduce merge {}))]
    (swap! storage
           (fn [stg]
             (update-in stg [:statuses]
                        merge new-statuses))))
  nil)

(defn get-user*
  [storage id]
  (get-in @storage [:users id]))

(defn get-status*
  [storage id]
  (get-in @storage [:statuses id]))

(defn get-timeline*
  [storage id]
  (filter #(= id (get-in % [:user :id]))
          (vals (:statuses @storage))))

(defn in-memory-only
  ([] (in-memory-only (atom {})))
  ([stg-atom]
   {:pre [(instance? clojure.lang.Atom stg-atom)
          (map? @stg-atom)]}
   (let [storage stg-atom]
     (reify Storage
       (store-user [_ user] (store-user* storage user))
       (store-status [_ status] (store-status* storage status))
       (store-timeline [_ timeline] (store-timeline* storage timeline))

       (get-user [_ id] (get-user* storage id))
       (get-status [_ id] (get-status* storage id))
       (get-timeline [_ id] (get-timeline* storage id))))))
