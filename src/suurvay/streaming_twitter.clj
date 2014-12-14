(ns suurvay.streaming-twitter
  "This namespace handles Twitter's streaming API. Its main purpose
  is to make a long-lived request to the statuses/filter endpoint."
  (:require [clojure.core.async :refer [chan <! <!! >! >!! go close!]]
            [clojure.walk :refer [keywordize-keys]]
            [schema.core :as sc]
            [suurvay.schema :refer [Status]]
            [clojure.string :as s]
            [butterfly.twitter :as butterfly] 
            [suurvay.async :as async :refer [while-let]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data, pure function, macro

(defrecord Tweet [id text screen-name
                  name profile user-creation-date
                  verified followers-count])

(defn- tweet->rec
  [tweet]
  (let [id          (get-in tweet ["user" "id"])
        name        (get-in tweet ["user" "name"])
        screen-name (get-in tweet ["user" "screen_name"] "")
        text        (get-in tweet ["text"])
        profile     (get-in tweet ["user" "description"] "")
        user-creation-date (get-in tweet ["user" "created_at"]) ;; untested
        verified   (get-in tweet ["user" "verified"]) ;; untested
        followers-count (get-in tweet ["user" "followers_count"])] ;; untested
    (if (and id text)
      (Tweet. id text screen-name
              name profile user-creation-date
              verified followers-count))))

(sc/defn keywordize-tweet :- Status
  [status]
  (let [{:strs [id text]} status]
    (when (and id text)
      (keywordize-keys status))))

(defn cond>!
  "Places val onto the given async channel iff it is truthy."
  [port val]
  (when val
    (go (>! port val))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Auth
(defn bf-creds
  [& creds-list]
  {:pre [(= 4 (count creds-list))]}
  (zipmap [:consumer-key :consumer-secret :access-token :access-secret] creds-list))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The important part
(defn filter-stream
  "Opens a new stream, placing Tweet objects onto the given channel if
  they match one or more of the items in `terms`. Returns a stream
  map, whose values contain methods for closing/cleaning up the session.

  Creds should be a map of the following form:
    {:consumer-key    \"consumer-key-string\"
     :consumer-secret \"consumer-secret-string\"
     :access-token    \"access-token-string\" 
     :access-secret   \"access-secret-string\"}"
  [creds ch terms]
  (let [search-str (s/join "," terms)
        chandler (comp (partial cond>! ch) keywordize-tweet)]
    (butterfly/start-streaming search-str
                               chandler
                               creds)))

