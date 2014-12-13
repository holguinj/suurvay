(ns suurvay.schema
  (:require [schema.core :as sc]
;            [twitter-schemas.core :as tsc]
            ))

(def NumStr
  (sc/both sc/Str
           (sc/pred (partial re-find #"^\d+$") "numeric string")))

(def Identifier
  (sc/either
    sc/Int
    NumStr
    sc/Str
    {:id sc/Int}
    {:user sc/Str}))

(def UserMap
  (sc/either {:user-id (sc/either NumStr sc/Int)}
             {:screen-name sc/Str}))

(def Hashtag
  (sc/pred (partial re-find #"#\w+$") "hashtag"))

(def ShortStatus
  {:text sc/Str})

(def Status
  {:retweeted sc/Bool
   :favorited sc/Bool
   :retweeted_status ShortStatus})

(def User
  {:id sc/Int
   :screen-name sc/Str
   :profile sc/Str
   :name sc/Str})
