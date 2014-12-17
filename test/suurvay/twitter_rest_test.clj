(ns suurvay.twitter-rest-test
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [schema.test :refer [validate-schemas]]
            [environ.core :refer [env]]
            [suurvay.twitter-rest :refer :all]
            [suurvay.schema :as ssc]))

(comment
  (def followers (c (get-followers "postpunkjustin")))

  (map #(c (get-followers %)) (take 16 followers))

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration and setup
(def test-creds
  "Reads and prepares Twitter API credentials from the environment using
  environ. :test-twitter-creds should be a map with the following
  keys:

  :consumer-key
  :consumer-secret
  :access-token
  :access-secret"
  (let [{:keys [consumer-key consumer-secret
                access-token access-secret]} (env :test-twitter-creds)]
    (make-oauth-creds consumer-key
                      consumer-secret
                      access-token
                      access-secret)))

(defn bind-creds-fixture [creds]
  (fn [f]
    (binding [*creds* creds]
      (f))))

(use-fixtures :once (bind-creds-fixture test-creds) validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities
(defmacro mocking
  [[f output] & body]
  `(with-redefs [~f (constantly ~output)]
     ~@body))

(defmacro c
  "Executes the body with bound test-creds. Only meant for use in the
  REPL--unnecessary otherwise because the creds are bound by a fixture."
  [& body]
  `(binding [*creds* ~test-creds]
     ~@body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
(deftest profile-test
  (testing "gets hashtags out of profiles and lower-cases them"
    (mocking [get-profile {:description "Hashtag #Twitter in the house. Hashtag #harassmentsucks."}]
      (let [hashtags (get-profile-hashtags "not a real account, output mocked")]
        (is (= #{"#twitter" "#harassmentsucks"} hashtags)))))) 

(deftest follow-test
  (testing "can get followers"
    (let [followers (get-followers "twitter")]
      (is (pos? (count followers)))))

  (testing "can get friends"
    (let [friends (get-friends "twitter")]
      (is (pos? (count friends)))
      (is (sc/validate [ssc/Identifier] friends)))))

(deftest timeline-test
  (testing "can get a timeline"
    (let [timeline (get-timeline "twitter")]
      (is (pos? (count timeline)))))

  (testing "can get hashtags for an account"
    (let [hashtags (get-user-hashtags "twitter")]
      ;; NOTE: this account can probably be relied upon to use a LOT
      ;; of hashtags, but this test would fail if it went long enough
      ;; without using one.
      (is (pos? (count hashtags))))))

(deftest names-and-ids
  (testing "can convert screen-name -> id -> name"
    (let [ids (names->ids ["twitter" "twitterapi"])
          names (ids->names ids)]
      (is (= #{"Twitter" "Twitter API"}
             (set names)))
      (is (= #{783214 6253282}
             (set ids))))))

(deftest identification-functions
  (testing "retrieves the correct real name"
    (testing "given a screen-name"
      (is (= "Twitter API"
             (get-name "twitterapi"))))
    (testing "given a Status object"
      (is (= "Tom Waits"
             (get-name {:user {:name "Tom Waits"
                               :id 12345
                               :description "Awesome dude"
                               :screen_name "tomwaits"}
                        :retweeted false
                        :favorited false
                        :text "Wow, Frank had some wild years."}))))
    (testing "given a user ID"
      (is (= "Twitter API"
             (get-name 6253282)))))

  (testing "retrieves the profile"
    (testing "for a username"
      (let [profile (get-profile "twitter")]
        (is (re-find #"Twitter" (:description profile)))))
    (testing "given a Status or User object"
      (let [profile "Funky fake profile!"
            user {:name "Funky Fake"
                  :id 12345
                  :screen_name "funky_fake"
                  :description "Funky fake profile!"}
            status {:user user
                    :retweeted false
                    :favorited false
                    :text "Super tweet."}]
        (is (= profile
               (:description (get-profile status))
               (:description (get-profile user)))))))

  (testing "retrieves a detailed user timeline"
    (testing "given a username"
      (let [timeline (get-timeline-details "twitterapi")]
        (is (= 200 (count timeline)))))
    (testing "given a Status object"
      (let [user {:name "Twitter API"
                  :id 6253282
                  :screen_name "twitterapi"
                  :description "The Twitter API"}
            status {:user user
                    :retweeted false
                    :favorited false
                    :text "Fake tweet. Not real."}
            timeline (get-timeline-details status)]
        (is (= 200
               (count timeline))))))

  (testing "retrieves a list of friends"
    (testing "given a username"
      (let [friends (get-friends "twitterapi")]
        (is (sc/validate [sc/Int] friends))
        (is (pos? (count friends)))))
    (testing "given a Status object"
      (let [user {:name "Twitter API"
                  :id 6253282
                  :screen_name "twitterapi"
                  :description "The Twitter API"}
            status {:user user
                    :retweeted false
                    :favorited false
                    :text "Fake tweet. Not real."}
            friends (get-friends status)]
        (is (sc/validate [sc/Int] friends))
        (is (pos? (count friends))))))

  (testing "retrieves a list of followers"
    (testing "given a username"
      (let [followers (get-followers "twitterapi")]
        (is (sc/validate [sc/Int] followers))
        (is (pos? (count followers)))))
    (testing "given a Status object"
      (let [user {:name "Twitter API"
                  :id 6253282
                  :screen_name "twitterapi"
                  :description "The Twitter API"}
            status {:user user
                    :retweeted false
                    :favorited false
                    :text "Fake tweet. Not real."}
            followers (get-followers status)]
        (is (sc/validate [sc/Int] followers))
        (is (pos? (count followers)))))))
