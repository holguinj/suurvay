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
            [twitter.api.restful :as t]))

;; TODO: Reuse the same map intended for streaming
(def make-oauth-creds #'twitter.oauth/make-oauth-creds)

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

(sc/defn next-reset :- sc/Int
  "Given a 'Rate limit exceeded' error from the Twitter API, returns
  the number of seconds until the next rate limit reset. Returns 901
  (15 minutes + 1 second) if there is an error parsing the response."
  [e :- Exception]
  (try
    (->> (str e)
      (re-find #"Next reset at (\d+)")
      second
      Integer/parseInt)
    (catch Exception _
      901)))

(sc/defn msec-until :- sc/Num
  "Given a time (in UTC epoch seconds), returns the number of
  milliseconds until that time."
  [target-time]
  (let [now-sec (/ (System/currentTimeMillis) 1000)
        delta-sec (- target-time now-sec)]
    (* delta-sec 1000)))

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
           (let [wait-time# (inc (msec-until (next-reset e#)))
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
  [identifier :- (sc/either Identifier Status User)]
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
  [identifier :- (sc/either Identifier User Status)]
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

(sc/defn get-id :- sc/Int
  "Given a user-id, user object, status object, or screen name, return
  the relevant user's ID."
  [identifier :- Identifier]
  (or (and (integer? identifier) identifier)                  ;; ID
      (get identifier :id)                                    ;; User or UserAbbrev
      (get-in identifier [:user :id])                         ;; Status
      (and (string? identifier) (-> identifier get-user :id)) ;; screen name
      (throw (IllegalArgumentException.
              (str "get-id called with this: " identifier)))))

(sc/defn get-profile :- User
  [identifier :- (sc/either Identifier User Status)]
  (cond
    (or (:profile identifier) (:screen_name identifier)) identifier ;; User

    (get-in identifier [:user :screen_name]) (:user identifier) ;; Status

    :else (get-user identifier))) ;; ID/screen name

(sc/defn get-profile-hashtags :- #{Hashtag}
  [identifier :- Identifier]
  (-> identifier
    get-profile
    :description
    get-hashtags))

(sc/defn get-name :- sc/Str
  "Given a user-id, user object, status object, or screen name, return
  the relevant user's real name."
  [identifier :- (sc/either Identifier Status User)]
  (or (get identifier :name) ;; User(Abbrev)
      (get-in identifier [:user :name]) ;; Status
      (and (string? identifier) (-> identifier get-user :name)) ;; screen_name
      (and (integer? identifier) (-> identifier get-user :name)) ;; ID
      (throw (IllegalArgumentException.
              (str "get-name called with this: " identifier)))))

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

(sc/defn get-all-blocks :- #{Identifier}
  ;;TODO: abstract the de-paging logic in here
  ([] (get-all-blocks -1))
  ([cursor]
   (try-with-limit
    (let [blocks-req #(t/blocks-ids :oauth-creds *creds* :params {:cursor %})
          resp (blocks-req cursor)
          blocks (->> resp :body :ids set)
          next-cursor (get-in resp [:body :next_cursor])]
      (if (zero? next-cursor)
        blocks
        (into blocks (get-all-blocks next-cursor)))))))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Debug
(defn print-creds
  []
  (println *creds*))
