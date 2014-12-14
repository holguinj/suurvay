(ns suurvay.twitter-rest
  "This namespace contains generic functions for interacting with the
  Twitter API."
  (:require [suurvay.twitter-pure :refer [num-string? identifier->map
                                          get-hashtags timeline-hashtags
                                          timeline-hashtags invert-frequencies
                                          retweet-statuses retweeted-users
                                          faved-retweeted-users
                                          timeline-endorsed-hashtags]]
            [clojure.string :as s]
            [suurvay.schema :refer [Identifier UserMap Hashtag Status User]]
            [schema.core :as sc]
            [twitter.oauth :refer [make-oauth-creds]]
            [twitter.api.restful :as t]))

;; TODO: Reuse the same map intended for streaming
(def make-ouauth-creds #'make-oauth-creds)

(def ^:dynamic *creds* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pure functions

(sc/defn rate-limit? :- sc/Bool
  "Returns true if the given exception was caused by hitting Twitter's
  API rate limit."
  [e :- Exception]
  (->> (str e)
    (re-find #"Rate limit exceeded")
    boolean))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros

(defmacro try-with-limit
  "Execute the body within a try-catch block. If it fails due to a
  Twitter rate limit, wait until the reset and then execute
  it again."
  [& body]
  `(let [body# (fn [] ~@body)]
     (try
       (body#)
       (catch Exception e#
         (if-not (rate-limit? e#)
           (throw e#)
           (let [wait-time# (inc (msec-until-reset))
                 wait-sec# (int (/ wait-time# 1000))]
             (println "Hit Twitter's rate limit! Waiting" wait-sec# "seconds.")
             (Thread/sleep wait-time#)
             (body#)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions that depend on the Twitter API

(sc/defn msec-until-reset :- sc/Num
  []
  (let [now (/ (System/currentTimeMillis) 1000)
        reset (-> (t/application-rate-limit-status :oauth-creds *creds*)
                (get-in [:headers :x-rate-limit-reset])
                Integer/parseInt)
        diff-sec (- reset now)]
    (* 1000 diff-sec)))

(sc/defn get-followers :- [Identifier]
  "Return a vector of follower IDs for the given user."
  [identifier :- Identifier]
  (let [user (identifier->map identifier)]
    (try-with-limit
     (-> (t/followers-ids :oauth-creds *creds* :params user)
       :body
       :ids))))

(defn get-friends
  "Return a vector of IDs for users that the given user is following."
  [identifier & {:as params}]
  (let [user (->> identifier identifier->map (merge params))]
    (try-with-limit
     (-> (t/friends-ids :oauth-creds *creds* :params user)
       :body
       :ids))))

(sc/defn ids->names :- [sc/Str]
  "Given a collection of Twitter IDs, return a new collection of
  screen-names for those users."
  [ids :- [Identifier]]
  (let [users (s/join "," ids)
        params {:user-id users, :include-entities false}]
    (try-with-limit
     (->> (t/users-lookup :oauth-creds *creds* :params params)
       :body
       (map :name)))))

(sc/defn names->ids :- [Identifier]
  [names :- [sc/Str]]
  (let [users (s/join "," names)
        params {:screen-name users, :include-entities false}]
    (try-with-limit
     (->> (t/users-lookup :oauth-creds *creds* :params params)
       :body
       (map :id)))))

(sc/defn get-timeline-details :- [Status]
  "Returns a sequence of the last 200 tweet objects for the given user."
  [identifier :- Identifier]
  (let [user (identifier->map identifier)
        params (merge user
                      {:include-rts true
                       :count 200
                       :trim-user true})]
    (try-with-limit
     (->> (t/statuses-user-timeline :oauth-creds *creds* :params params)
       :body))))

(sc/defn get-timeline :- [sc/Str]
  "Return a sequence of tweet bodies for the given user. Removes
  retweets."
  [identifier :- Identifier]
  (try-with-limit
   (->> (get-timeline-details identifier)
     (filter (complement :retweeted))
     (map :text))))

(sc/defn get-user-hashtags :- #{Hashtag}
  "Returns the set of hashtags recently used by the given user."
  [identifier :- Identifier]
  (let [tweets (get-timeline identifier)]
    (->> tweets
      (mapcat get-hashtags)
      (map s/lower-case)
      (into #{}))))

(sc/defn get-users-hashtags :- {sc/Int [Hashtag]}
  "Given a collection of user IDs or names, return a sorted map from tag
  frequency to tags."
  [identifiers :- [Identifier]]
  (->> identifiers
    (mapcat get-user-hashtags)
    frequencies
    invert-frequencies))

(sc/defn get-user :- User
  "Returns a complete user object for the given user."
  [identifier]
  (let [user (identifier->map identifier)
        params (assoc user :include-entities false)]
    (try-with-limit
     (-> (t/users-show :oauth-creds *creds* :params params)
       (get :body)))))

(sc/defn get-profile :- User
  [identifier :- Identifier]
  (if (or (:profile identifier) (:name identifier))
    identifier 
    (-> identifier
      get-user)))

(sc/defn get-profile-hashtags :- #{Hashtag}
  [identifier :- Identifier]
  (-> identifier
    get-profile
    :description
    get-hashtags))

(sc/defn get-name :- sc/Str
  [identifier :- Identifier]
  (if-let [screen-name (:screen-name identifier)]
    screen-name
    (cond
     (and (string? identifier) (not (num-string? identifier)))
     identifier

     :else
     (-> identifier get-user :screen_name))))

(sc/defn get-blocks :- #{Identifier}
  ([] (get-blocks nil))
  ([identifier :- Identifier]
   (let [base-params {}
         params (if identifier
                  (merge base-params (identifier->map identifier))
                  base-params)]
     (try-with-limit
      (->> params
        (t/blocks-ids :oauth-creds *creds* :params)
        :body
        :ids
        set)))))

(sc/defn block! :- sc/Bool
  [identifier :- Identifier]
  (let [user (identifier->map identifier)
        params (assoc user :include-entities false, :skip-status true)]
    (try-with-limit
     (t/blocks-create :oauth-creds *creds* :params params)
     true)))

(sc/defn unblock! :- sc/Bool
  [identifier :- Identifier]
  (let [user (identifier->map identifier)
        params (assoc user :include-entities false, :skip-status true)]
    (try-with-limit
     (t/blocks-destroy :oauth-creds *creds* :params params)
     true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Enter at your own risk!

(defn de-page
  "Given a request function (which takes only a cursor) and an initial
  response, return the bodies of all pages.
  
  Example with `get-following` (note that only functions that take `& {:as params}` will work this way):
    (let [get-following-cursor #(get-following \"user\" :cursor %)]
      (de-page get-following-cursor)

  NOTE: Twitter's rate limiting is really, really bad.
  You probably shouldn't use this function."
  [req-fn {{:keys [next_cursor]} :body
           body :body
           :as resp}]
  (loop [acc [body]
         cursor next_cursor]
    (if (= 0 cursor) ;; this definitely came back as an int
      acc
      (let [next-res (try-with-limit (req-fn cursor))
            next-cursor (get-in next-res [:body :next_cursor])]
        (recur (conj acc (:body next-res))
               next-cursor)))))

