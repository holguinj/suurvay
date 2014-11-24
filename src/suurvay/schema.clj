(ns suurvay.schema
  (:require [schema.core :as sc]
            [twitter-schemas.core :as tsc]))

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
  (sc/both sc/Str
           (sc/pred (partial re-find #"#\w+$") "hashtag")))

