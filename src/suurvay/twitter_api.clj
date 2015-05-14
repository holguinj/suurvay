(ns suurvay.twitter-api
  "Contains the TwitterAPI protocol and its implementations"
  (:require [suurvay.twitter-rest :as t]
            [suurvay.twitter-pure :as tp]
            [suurvay.schema :refer [Status User] :as ssc]
            [suurvay.storage :as st]))

(defprotocol TwitterAPI
  (get-name [this identifier] "Returns the 'real name' of the given identifier.")
  (get-profile [this identifier] "Returns the profile text for the given identifier.")
  (get-timeline-details [this identifier] "Returns the timeline with tweet details for the given identifier.")
  (get-friends [this identifier] "Returns a vector of user IDs that the user identified is following.")
  (get-followers [this identifier] "Returns a vector of user IDs following the identified user.")
  (block! [this identifier] "Blocks the given user on Twitter. Requires user-level credentials.")
  (unblock! [this identifier] "Unblocks the given user on Twitter. Requires user-level credentials."))

(defn maybe-user-creds
  "Given a creds object which may be a multi-creds atom, return what
  is definitely a single-creds object or nil."
  [creds]
  (cond
    (ssc/user-creds? creds)
    creds

    (ssc/multi-creds? creds)
    (if-let [maybe-creds (->> @creds (filter ssc/user-creds?) first)]
      maybe-creds)

    :else
    nil)) ;; TODO: log that blocking/unblocking won't be possible

(defn ensuring-creds
  "Calls f with the given creds object, if it is not nil. Otherwise,
  throws an error."
  [creds f]
  (if creds
    (f creds)
    (throw (IllegalArgumentException. (str "This object was not provided user credentials,"
                                           " so it can not block/unblock users.")))))

(defn twitter-api
  "Returns a TwitterAPI object wrapping the given credentials."
  [creds]
  (let [user-creds (maybe-user-creds creds)]
    (reify TwitterAPI
      (get-name [_ identifier] (t/get-name creds identifier))
      (get-profile [_ identifier] (t/get-profile creds identifier))
      (get-timeline-details [_ identifier] (t/get-timeline-details creds identifier))
      (get-friends [_ identifier] (t/get-friends creds identifier))
      (get-followers [_ identifier] (t/get-followers creds identifier))
      (block! [_ identifier] (ensuring-creds user-creds #(t/block! % identifier)))
      (unblock! [_ identifier] (ensuring-creds user-creds #(t/unblock! % identifier))))))

(defn twitter-api-with-storage
  "Closes over the given credentials and storage object. Stores
  Twitter results in the storage object, but does not use it for
  retrieval."
  [creds storage]
  (let [user-creds (maybe-user-creds creds)]
    (reify TwitterAPI
      (get-name [_ identifier]
        (t/get-name creds identifier))

      (get-profile [_ identifier]
        (let [user (t/get-profile creds identifier)]
          (future (st/store-user storage user))
          user))

      (get-timeline-details [_ identifier]
        (let [timeline (t/get-timeline-details creds identifier)]
          (future (st/store-timeline storage timeline))
          timeline))

      (get-friends [_ identifier]
        (let [id (t/get-id creds identifier)
              friends (t/get-friends creds identifier)]
          (future (st/store-friends storage id friends))
          friends))

      (get-followers [_ identifier]
        (let [id (t/get-id creds identifier)
              followers (t/get-followers creds identifier)]
          (future (st/store-followers storage id followers))
          followers))

      (block! [_ identifier]
        (ensuring-creds user-creds #(t/block! % identifier)))

      (unblock! [_ identifier]
        (ensuring-creds user-creds #(t/unblock! % identifier))))))

(defn twitter-api-storage-only
  [storage]
  (reify TwitterAPI
    (get-name [this identifier]
      (:name (get-profile this identifier)))

    (get-profile [this identifier]
      (let [id (tp/get-id identifier)]
       (st/get-user storage id)))

    (get-timeline-details [this identifier]
      (let [id (tp/get-id identifier)]
        (st/get-timeline storage id)))

    (get-friends [this identifier]
      (let [id (tp/get-id identifier)]
        (st/get-friends storage id)))

    (get-followers [this identifier]
      (let [id (tp/get-id identifier)]
        (st/get-followers storage id)))

    (block! [this identifier]
      ;; TODO
      nil)

    (unblock! [this identifier]
      ;; TODO
      nil)))
