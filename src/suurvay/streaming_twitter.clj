(ns suurvay.streaming-twitter
  "This namespace handles Twitter's streaming API. It's main purpose
  is to make a long-lived request to the statuses/filter endpoint."
  (:require [clojure.core.async :refer [chan <! <!! >! >!! go close!]]
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

(defmacro cond>!
  [port val]
  `(go
     (when ~val
       (>! ~port ~val))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Auth
(defn bf-creds
  [& creds-list]
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
        chandler (comp #(cond>! ch %) tweet->rec)]
    (butterfly/start-streaming search-str
                               chandler
                               creds)))

