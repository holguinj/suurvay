(ns suurvay.twitter-streaming-test
  (:require [clojure.core.async :refer [chan <!! <! >!! >! close! go]]
            [clojure.test :refer :all]
            [schema.test :refer [validate-schemas]]
            [schema.core :as sc]
            [environ.core :refer [env]]
            [suurvay.async :refer [while-let]]
            [suurvay.schema :refer [Status]]
            [suurvay.streaming-twitter :refer [filter-stream]]))

(def test-creds
  "Reads and prepares Twitter API credentials from the environment using
  environ. :test-twitter-creds should be a map with the following
  keys only:

  :consumer-key
  :consumer-secret
  :access-token
  :access-secret"
  (:test-twitter-creds env))

(use-fixtures :once validate-schemas)

(defn temporary-tweet-collector
  [seconds]
  (println "Streaming tweets for" seconds "seconds.")
  (let [msec (* seconds 1000)
        ch (chan)
        stream (filter-stream test-creds
                              ch
                              ["RT" "twitter" "MT"])]
    (Thread/sleep msec)
    (deliver (:done stream) true)
    (close! ch)
    ch))

(defn assert-tweet
  [tweet]
  (try (sc/validate Status tweet)
       true
       (catch clojure.lang.ExceptionInfo _
         (println "The following tweet failed to validate:")
         (clojure.pprint/pprint tweet)
         false)))

(deftest stream-test
  (testing "get some tweets"
    (let [t-ch (temporary-tweet-collector 10)
          results (atom ())]
      (while-let [t (<!! t-ch)]
                 (swap! results conj (assert-tweet t)))
      (let [success-count  (->> @results (filter true?) count)
            failure-count  (->> @results (filter false?) count)]
        (println success-count "tweets successfully streamed with" failure-count "failures.")

        (testing "and ensure that at least one tweet was processed"
          (is (pos? success-count)))

        (testing "and ensure there are no failures"
          (is (zero? failure-count)))))))
