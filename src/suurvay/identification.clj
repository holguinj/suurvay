(ns suurvay.identification
  "This namespace contains functions that evaluate an unknown user and
  attempt to determine group membership."
  (:require [clojure.set :as set]
            [suurvay.twitter :as t]
            [suurvay.schema :refer [Status User]]))

(def test-order
  "The first element in each vector is a key to look up in the tests
  map. The second element is a function that will provide the
  raw data used to call the corresponding function in the tests map.

  For example, [:profile t/get-profile] will eventually expand to
  something like: `((:profile tests-map) (t/get-profile subject))`."
  [[:before      identity] ;; check whitelists, debug, etc.
   [:real-name   t/get-name]
   [:profile     t/get-profile]
   [:timeline    t/get-timeline-details]
   [:friends     t/get-friends]
   [:followers   t/get-followers]])

(defn test-runner
  "Takes a tests map of the following form:
    {:limit       Number
     :before      (Status        -> score)
     :real-name   (RealName      -> score)
     :profile     (User          -> score)
     :timeline    ([Status]      -> score)
     :friends     (FriendsList   -> score)
     :followers   (FollowersList -> score)}

  and applies the given functions in this order to the relevant data
  from Twitter. If the cumulative score ever meets or surpasses the
  limit, the score will be returned immediately. Otherwise all tests
  are evaluated and then the score is returned.

  Note that tests CAN return negative numbers. If the cumulative score
  is ever negative then the tests will terminate immediately and the
  negative score will be returned.

  If you omit a key, that API endpoint will be skipped."
  [{:keys [limit] :as tests} subject]
  (loop [acc 0
         rem-tests test-order]
    (if (or (>= acc limit) (neg? acc) (empty? rem-tests))
      acc
      (let [[test-fn-key twitter-fn] (first rem-tests)
            test-fn (get tests test-fn-key)
            new-score (if-not test-fn 0 (-> subject twitter-fn test-fn))]
        (recur (+ acc new-score)
               (rest rem-tests))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility functions that should be useful when writing identification functions
(defn count-hits
  "Given two sets, return the number of items they have in common."
  [comparison-set subject-set]
  (-> (set/intersection comparison-set subject-set)
    count))

(defn regex-hits
  "Returns the number of times that any of the given regexps are found
  in s. Note that a single regex may be counted multiple times."
  [regexps s]
  (let [s (or s "")
        all-matches (mapcat #(re-seq % s) regexps)]
    (count all-matches)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Abstract scoring functions

;; It's not clear to me that these still have a place in the codebase.
;; They're based on an earlier idea of how classification would work.
(defn score
  "Calls (f subject) and checks the ratio of intersecting data points
  with suspect-data. `f` should be of type subject -> set."
  [f suspect-data subject]
  (let [subject-results (f subject)
        highest (count suspect-data)]
    (-> subject-results
      (set/intersection suspect-data)
      count
      (/ highest))))

(defn identify->*
  "Threads the subject through `score` with the given f-data-pairs
  until they are exhausted or until the (cumulative) limit is
  reached. If not supplied, limit will be 1.

  Each element in `f-data-pairs` should have the form [f suspect-data]."
  ([f-data-pairs subject] (identify->* f-data-pairs subject 1))
  ([f-data-pairs subject limit]
   (loop [score-acc 0
          remaining-pairs f-data-pairs]
     (if (or (<= limit score-acc) (empty? remaining-pairs))
       score-acc
       (let [[f suspect-data] (first remaining-pairs)
             new-score (+ score-acc (score f suspect-data subject))]
         (recur new-score
                (rest remaining-pairs)))))))

(defmacro identify->
  "Macronic version of identify->*"
  [subj & f-data-pairs]
  (let [score (and (number? (first f-data-pairs)) (first f-data-pairs))
        vec-pairs (if score
                    (map vec (rest f-data-pairs))
                    (map vec f-data-pairs))]
    (if score
      `(identify->* [~@vec-pairs] ~subj ~score)
      `(identify->* [~@vec-pairs] ~subj))))
