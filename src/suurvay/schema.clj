(ns suurvay.schema
  (:require [schema.core :as sc]))

(def NumStr
  (sc/pred (partial re-find #"^\d+$") "numeric string"))

(def UserMap
  (sc/either {:user-id (sc/either NumStr sc/Int)}
             {:screen-name sc/Str}))

(def Hashtag
  (sc/pred (partial re-find #"#\w+$") "hashtag"))

(def ShortStatus
  {:text sc/Str
   sc/Keyword sc/Any})

(def Status
  {:retweeted sc/Bool
   :text sc/Str
   :favorited sc/Bool
   (sc/optional-key :retweeted_status) ShortStatus
   sc/Keyword sc/Any})

(def User
  {:id sc/Int
   :screen_name sc/Str
   :description sc/Str
   :name sc/Str
   sc/Keyword sc/Any})

(def Identifier
  (sc/either
    sc/Int
    NumStr
    sc/Str
    {:id sc/Int}
    {:user sc/Str}
    Status
    User))

(defn user-creds?
  [x]
  (and (map? x)
       (= #{:consumer :access-token :access-token-secret}
          (-> x keys set))))

(def AppCreds
  {:bearer sc/Str})

(def UserCreds
  (sc/pred user-creds?))

(def atom? (partial instance? clojure.lang.Atom))

(defn app-creds?
  [x]
  (and (map? x)
       (= '(:bearer) (keys x))
       (every? string? (vals x))))

(defn multi-creds?
  [x]
  (and (atom? x)
       (vector? @x)
       (every? #(or (user-creds? %)
                    (app-creds? %))
               @x)))

(def MultiCreds
  (sc/pred multi-creds?))
