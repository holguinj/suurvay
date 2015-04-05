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
