(ns suurvay.twitter-pure
  "This namespace contains generic functions for interacting with the
  Twitter API. Credentials are read from the environment, so they
  should be provided either through your shell or using the
  lein-environ plugin."
  (:require [clojure.string :as s] 
            [suurvay.schema :refer [Identifier Hashtag UserMap Status]]
            [schema.core :as sc]
;            [twitter-schemas.keywordized :as tsc :refer [User Status]]
            [twitter.oauth :refer [make-oauth-creds]]
            [twitter.api.restful :as t]))

;; TODO: Reuse the same map intended for streaming
(def make-ouauth-creds #'make-oauth-creds)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pure functions

(sc/defn num-string? :- sc/Bool
  "Returns true if the given string (or whatever) is numeric."
  [x :- sc/Any]
  (boolean (and (string? x) (re-find #"^\d+$" x))))

(sc/defn identifier->map :- UserMap
  "If x is a number or appears to be a stringified user ID, return
  {:user-id x}, otherwise return {:screen-name x}."
  [x :- Identifier]
  (if-let [id (:id x)]
    {:user-id id}
    (cond
     (or (number? x) (num-string? x))
     {:user-id x}

     (string? x)
     {:screen-name x})))

(sc/defn get-hashtags :- #{Hashtag}
  "Given a string, return a set of all hashtags (including the #
  symbol) present in the string."
  [text]
  (->> (or text "")
    (re-seq #"#\w+")
    (map s/lower-case)
    set))

(sc/defn timeline-hashtags :- #{Hashtag}
  [tweets :- [Status]]
  (->> tweets
    (mapcat (comp get-hashtags :text))
    set))

(defn invert-frequencies
  "Given a map of the kind returned by frequencies, return a sorted
  map (descending) where the keys are the frequencies and all matching
  values are included."
  [m]
  (let [freqs (vals m)]
    (into (sorted-map-by >=)
          (for [f freqs]
            [f (->> m
                 (filter (fn [[_ v]] (= f v)))
                 (map first))]))))

(defn rate-limit?
  "Returns true if the given exception was caused by hitting Twitter's
  API rate limit."
  [e]
  (->> (str e)
    (re-find #"Rate limit exceeded")
    boolean))

(sc/defn retweet-statuses
  "Given a collection of tweets from the Twitter API, return a
  sequence of retweet bodies."
  [tweets :- [Status]]
  (->> tweets
    (filter :retweeted)
    (map :retweeted_status)))

(sc/defn retweeted-users :- #{Identifier}
  "Given a collection of tweets (with deets) from the Twitter API,
  return a set of users who were retweeted."
  [tweets :- [Status]]
  (->> tweets
    retweet-statuses
    (map #(get-in % [:user :id]))
    set))

(sc/defn endorsed?
  "Returns true if the given tweet is either not a retweet, or a
  retweet that was also favorited."
  [tweet :- Status]
  (or (not (:retweeted tweet))
      (:favorited tweet)))

(sc/defn timeline-endorsed-hashtags :- [Hashtag]
  [tweets :- [Status]]
  (->> tweets
    (filter endorsed?)
    timeline-hashtags))

(sc/defn faved-retweeted-users :- #{Identifier}
  "Similar to retweeted-users, but returns only users whose tweets
  were favorited+retweeted."
  [tweets :- [Status]]
  (->> tweets
    retweet-statuses
    (filter :favorited)
    (map #(get-in % [:user :id]))
    set))

