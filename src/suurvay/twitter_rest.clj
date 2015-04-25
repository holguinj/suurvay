(ns suurvay.twitter-rest
  "This namespace contains generic functions for interacting with the
  Twitter API."
  (:require [suurvay.twitter-pure :as pure :refer [num-string? identifier->map
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pure functions

(def atom? (partial instance? clojure.lang.Atom))

(sc/defn rate-limit? :- sc/Bool
  "Returns true if the given exception was caused by hitting Twitter's
  API rate limit."
  [e :- Exception]
  (->> (str e)
    (re-find #"Rate limit exceeded")
    boolean))

(sc/defn rotate :- [sc/Any]
  "Returns a vector of the elements in coll where the first element
  has been moved to the end of the vector."
  [coll :- [sc/Any]]
  (if-not (seq coll)
    []
    (conj (vec (rest coll))
          (first coll))))

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

(defn try-with-multi-creds
  [multi-creds twitter-fn & args]
  (let [twit #(apply twitter-fn :oauth-creds % args)
        creds (first @multi-creds)]
    (try (twit creds)
      (catch Exception e
        (if-not (rate-limit? e)
          (throw e)
          (let [creds (first (swap! multi-creds rotate))]
            (println "Hit Twitter's rate limit! Retrying with alternate credentials.")
            (try (twit creds)
              (catch Exception e
                (if-not (rate-limit? e)
                  (throw e)
                  (let [wait-time (+ 1000 (msec-until (next-reset e)))
                        ;; including a one-second buffer
                        wait-sec (inc (int (/ wait-time 1000)))]
                    (println "Hit Twitter's rate limit again! Waiting" wait-sec "seconds.")
                    (Thread/sleep wait-time)
                    (twit creds)))))))))))

(defn try-with-single-creds
  [creds twitter-fn & args]
  (if (atom? creds)
    (throw (IllegalArgumentException. "Can't call this function with multi-creds")))
  (let [thunk #(apply twitter-fn :oauth-creds creds args)]
    (try
        (thunk)
        (catch Exception e
          (if-not (rate-limit? e)
            (throw e)
            (let [wait-time (+ 1000 (msec-until (next-reset e)))
                  ;; including a one-second buffer
                  wait-sec (inc (int (/ wait-time 1000)))]
              (println "Hit Twitter's rate limit! Waiting" wait-sec "seconds.")
              (Thread/sleep wait-time)
              (thunk)))))))

(defn try-with-creds
  "Calls twitter-fn using creds and any additional args. If
  it hits the Twitter rate limit, waits until the reset and then
  execute it again."
  [creds twitter-fn & args]
  (if (atom? creds)
    (apply try-with-multi-creds creds twitter-fn args)
    (apply try-with-single-creds creds twitter-fn args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions that depend on the Twitter API

(sc/defn msec-until-reset :- sc/Num
  [creds]
  (let [now (/ (System/currentTimeMillis) 1000)
        reset (-> (t/application-rate-limit-status :oauth-creds creds)
                (get-in [:headers :x-rate-limit-reset])
                Integer/parseInt)
        diff-sec (- reset now)]
    (* 1000 diff-sec)))

(sc/defn get-followers :- [sc/Int]
  "Return a vector of follower IDs for the given user."
  [creds identifier :- (sc/either Identifier Status User)]
  (let [user (identifier->map identifier)]
    (-> (try-with-creds creds t/followers-ids :params user)
      :body
      :ids)))

(defn get-friends
  "Return a vector of IDs for users that the given user is following."
  [creds identifier]
  (let [user (identifier->map identifier)]
    (-> (try-with-creds creds t/friends-ids :params user)
      :body
      :ids)))

(sc/defn ids->names :- [sc/Str]
  "Given a collection of Twitter IDs, return a new collection of
  screen-names for those users."
  [creds ids :- [Identifier]]
  (let [users (s/join "," ids)
        params {:user-id users, :include-entities false}]
    (->> (try-with-creds creds t/users-lookup :params params)
      :body
      (map :name))))

(sc/defn names->ids :- [Identifier]
  [creds names :- [sc/Str]]
  (let [users (s/join "," names)
        params {:screen-name users, :include-entities false}]
    (->> (try-with-creds creds t/users-lookup :params params)
      :body
      (map :id))))

(sc/defn get-timeline-details :- [Status]
  "Returns a sequence of the last 200 tweet objects for the given user."
  [creds identifier :- (sc/either Identifier User Status)]
  (let [user (identifier->map identifier)
        params (merge user
                      {:include-rts true
                       :count 200
                       :trim-user true})]
    (->> (try-with-creds creds t/statuses-user-timeline :params params)
      :body)))

(sc/defn get-timeline :- [sc/Str]
  "Return a sequence of tweet bodies for the given user. Removes
  retweets."
  [creds identifier :- Identifier]
  (->> (get-timeline-details creds identifier)
    (filter (complement :retweeted))
    (map :text)))

(sc/defn get-user-hashtags :- #{Hashtag}
  "Returns the set of hashtags recently used by the given user."
  [creds identifier :- Identifier]
  (let [tweets (get-timeline creds identifier)]
    (->> tweets
      (mapcat get-hashtags)
      (map s/lower-case)
      (into #{}))))

(sc/defn get-users-hashtags :- {sc/Int [Hashtag]}
  "Given a collection of user IDs or names, return a sorted map from tag
  frequency to tags."
  [creds identifiers :- [Identifier]]
  (->> identifiers
    (mapcat (partial get-user-hashtags creds))
    frequencies
    invert-frequencies))

(sc/defn get-user :- User
  "Returns a complete user object for the given user."
  [creds identifier]
  (let [user (identifier->map identifier)
        params (assoc user
                      :include-entities false)]
    (-> (try-with-creds creds t/users-show :params params)
      :body)))

(sc/defn get-id :- sc/Int
  "Given a user-id, user object, status object, or screen name, return
  the relevant user's ID."
  [creds identifier :- Identifier]
  (or (pure/get-id identifier)                                     ;; ID, Status, or User object
      (and (string? identifier) (:id (get-user creds identifier))) ;; screen name
      (throw (IllegalArgumentException.                            ;; WTF
              (str "get-id called with this: " identifier)))))

(sc/defn get-profile :- User
  [creds identifier :- (sc/either Identifier User Status)]
  (cond
    (or (:profile identifier) (:screen_name identifier)) identifier ;; User

    (get-in identifier [:user :screen_name]) (:user identifier) ;; Status

    :else (get-user creds identifier))) ;; ID/screen name

(sc/defn get-profile-hashtags :- #{Hashtag}
  [creds identifier :- Identifier]
  (-> (get-profile creds identifier)
    :description
    get-hashtags))

(sc/defn get-name :- sc/Str
  "Given a user-id, user object, status object, or screen name, return
  the relevant user's real name."
  [creds identifier :- (sc/either Identifier Status User)]
  (or (get identifier :name) ;; User(Abbrev)
      (get-in identifier [:user :name]) ;; Status
      (and (string? identifier) (:name (get-user creds identifier))) ;; screen_name
      (and (integer? identifier) (:name (get-user creds identifier))) ;; ID
      (throw (IllegalArgumentException.
              (str "get-name called with this: " identifier)))))

(sc/defn get-blocks :- #{Identifier}
  ([creds]
   (-> (try-with-single-creds creds t/blocks-ids)
     :body
     :ids
     set)))

(sc/defn get-all-blocks :- #{Identifier}
  ;;TODO: abstract the de-paging logic in here
  ([creds] (get-all-blocks creds -1))
  ([creds cursor]
   (let [blocks-req #(try-with-single-creds t/blocks-ids :params {:cursor %})
         resp (blocks-req cursor)
         blocks (-> resp :body :ids set)
         next-cursor (get-in resp [:body :next_cursor])]
     (if (zero? next-cursor)
       blocks
       (into blocks (get-all-blocks creds next-cursor))))))

(sc/defn block! :- User
  [creds identifier :- Identifier]
  (let [user (identifier->map identifier)
        params (assoc user :include-entities false, :skip-status true)]
    (try-with-creds creds t/blocks-create :params params)
    true))

(sc/defn unblock! :- User
  [creds identifier :- Identifier]
  (let [user (identifier->map identifier)
        params (assoc user :include-entities false, :skip-status true)]
    (try-with-creds creds t/blocks-destroy :params params)))
