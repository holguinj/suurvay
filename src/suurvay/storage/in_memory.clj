(ns suurvay.storage.in-memory
  (:require [suurvay.storage :refer [Storage]]))

(def blank-storage-map
  {:users {}      ;; user-id -> user
   :statuses {}   ;; status-id -> status
   :follows #{}}) ;; [id following]

(defn store-user*
  [storage {:keys [id] :as user}]
  (swap! storage
         (fn [stg]
           (update-in stg [:users]
                      assoc id user))))

(defn store-status*
  [storage {:keys [id] :as status}]
  (swap! storage
         (fn [stg]
           (update-in stg [:statuses]
                      assoc id status))))

(defn store-timeline*
  [storage statuses]
  (let [new-statuses (->> (for [{:keys [id] :as status} statuses]
                            {id status})
                       (reduce merge {}))]
    (swap! storage
           (fn [stg]
             (update-in stg [:statuses]
                        merge new-statuses)))))

(defn store-followers*
  [storage id followers]
  (let [new-follows (for [follower followers]
                      [follower id])]
    (swap! storage
           (fn [stg]
             (update-in stg [:follows]
                        into new-follows)))))

(defn store-friends*
  [storage id friends]
  (let [new-follows (for [friend friends]
                      [id friend])]
    (swap! storage
           (fn [stg]
             (update-in stg [:follows]
                        into new-follows)))))

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

(defn get-followers*
  [storage id]
  (->> (:follows @storage)
    (filter (fn [[user follows]] (= id follows)))
    (map first)))

(defn get-friends*
  [storage id]
  (->> (:follows @storage)
    (filter (fn [[user follows]] (= id user)))
    (map second)))

(def ->nil (constantly nil))

(defn in-memory-only
  ([] (in-memory-only (atom blank-storage-map)))
  ([stg-atom]
   {:pre [(instance? clojure.lang.Atom stg-atom)
          (map? @stg-atom)]}
   (let [storage stg-atom]
     (reify Storage
       (store-user [_ user] (->nil (store-user* storage user)))
       (store-status [_ status] (->nil (store-status* storage status)))
       (store-timeline [_ timeline] (->nil (store-timeline* storage timeline)))
       (store-followers [_ id followers] (->nil (store-followers* storage id followers)))
       (store-friends [_ id friends] (->nil (store-friends* storage id friends)))

       (get-user [_ id] (get-user* storage id))
       (get-status [_ id] (get-status* storage id))
       (get-timeline [_ id] (get-timeline* storage id))
       (get-followers [_ id] (get-followers* storage id))
       (get-friends [_ id] (get-friends* storage id))))))
