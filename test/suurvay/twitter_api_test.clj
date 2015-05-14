(ns suurvay.twitter-api-test
  (:require [suurvay.twitter-api :refer :all]
            [clojure.test :refer :all]
            [environ.core :refer [env]]
            [suurvay.storage.in-memory :refer [in-memory-only]]
            [suurvay.twitter-rest :refer [make-oauth-creds]]
            [suurvay.storage-test :refer [my-id]]
            [suurvay.schema :as sc]
            [schema.core :refer [validate Int]]))

(def test-creds
  "Reads and prepares Twitter API credentials from the environment using
  environ. :test-twitter-creds should be a map with the following
  keys:

  :consumer-key
  :consumer-secret
  :access-token
  :access-secret"
  (if-let [{:keys [consumer-key consumer-secret
                   access-token access-secret]} (env :test-twitter-creds)]
    (make-oauth-creds consumer-key
                      consumer-secret
                      access-token
                      access-secret)
    (throw (Exception. (str "Could not locate :test-twitter-creds in the environment!"
                            " See doc/testing.md for more information.")))))

(deftest ^:acceptance twitter-api-test
  (let [stg (in-memory-only)
        twitter-rest (twitter-api-with-storage test-creds stg)
        ;; we'll retrieve these now and validate them later
        name (get-name twitter-rest my-id)
        profile (get-profile twitter-rest my-id)
        timeline (get-timeline-details twitter-rest my-id)
        friends (get-friends twitter-rest my-id)
        followers (get-followers twitter-rest my-id)]
    (testing "the retrieved data makes sense:"
      (testing "name"
        (is (string? (not-empty name))))
      (testing "profile"
        (is (validate sc/User profile)))
      (testing "timeline"
        (is (validate [sc/Status] timeline)))
      (testing "friends"
        (is (validate [Int] friends)))
      (testing "followers"
        (is (validate [Int] followers))))

    (testing "the storage-only implementation returns the same data"
      (let [twitter-stg (twitter-api-storage-only stg)]
        (testing "for:"
          (testing "name"
            (is (= name (get-name twitter-stg my-id))))
          (testing "profile"
            (is (= profile (get-profile twitter-stg my-id))))
          (testing "timeline"
            (is (= (set timeline)
                   (set (get-timeline-details twitter-stg my-id)))))
          (testing "friends"
            (is (= (set friends)
                   (set (get-friends twitter-stg my-id)))))
          (testing "followers"
            (is (= (set followers)
                   (set (get-followers twitter-stg my-id))))))))))
