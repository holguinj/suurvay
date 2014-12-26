(ns suurvay.twitter-rest-test
  (:import (java.util.concurrent TimeoutException))
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [schema.test :refer [validate-schemas]]
            [environ.core :refer [env]]
            [suurvay.twitter-rest :refer :all]
            [twitter.api.restful :as tw]
            [suurvay.schema :as ssc]))

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
  (if-let [{:keys [consumer-key consumer-secret
                   access-token access-secret]} (env :test-twitter-creds)]
    (make-oauth-creds consumer-key
                      consumer-secret
                      access-token
                      access-secret)
    (throw (Exception. "Could not locate :test-twitter-creds in the environment! See doc/testing.md for more information."))))

(def app-only-test-creds
  "Depends on test-creds"
  (let [{:keys [consumer-key consumer-secret]} (env :test-twitter-creds)]
    (make-oauth-creds consumer-key
                      consumer-secret)))

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

(defn invoke-timeout [f timeout-ms]
  (let [thr (Thread/currentThread)
        fut (future (Thread/sleep timeout-ms)
                    (.interrupt thr))]
    (try (f)
         (catch InterruptedException e
           (throw (TimeoutException. "Execution timed out!")))
         (finally
           (future-cancel fut)))))

(defmacro timeout
  [ms & body]
  `(invoke-timeout (fn [] ~@body) ~ms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
(deftest pure-functions
  (testing "rotate"
    (testing "operates correctly on vectors"
      (is (= [2 3 1]
             (rotate [1 2 3]))))
    (testing "returns a vector when passed a list"
      (is (= [:b :c :a]
             (rotate '(:a :b :c)))))
    (testing "n rotations of an n-length coll returns the original coll"
      (is (= [1 2 3]
             (rotate (rotate (rotate [1 2 3]))))))))

(deftest auth-test
  (let [{:keys [consumer-key consumer-secret]} (env :test-twitter-creds)
        app-only (make-oauth-creds consumer-key consumer-secret)
        limit-with-creds (comp #(get-in % [:body :resources :statuses :/statuses/user_timeline :limit])
                               #(tw/application-rate-limit-status :oauth-creds %))]
    (testing "The API should provide a higher limit count when app-only auth is used"
      (let [user-limit (limit-with-creds test-creds)
            app-limit (limit-with-creds app-only)]
        ;; If these tests ever fail, it could be due to Twitter changing the rate limits
        (is (= 180 user-limit))
        (is (= 300 app-limit))))))

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

(deftest multi-creds-test
  (testing "switches to alternate creds when necessary"
    (println "About to try to pass Twitter's rate limit")
    (timeout 15000
      (binding [*multi-creds* (atom [app-only-test-creds test-creds])]
        (println "Waiting up to 15 seconds for test to complete.")
        (let [twenty-users (take 20 (get-followers "twitterapi"))
              ;; the rate limit for get-followers is 15, so we'll try to
              ;; grab 20 users' followers
              ;; We need both the string output and the return value,
              ;; so this is pretty weird code.
              friends-of-followers (promise)
              result-out (with-out-str (deliver friends-of-followers
                                                (doall (map get-friends twenty-users))))]
          (is (= 20 (count twenty-users)))
          (is (= 20 (count @friends-of-followers)))
          (is (every? (comp pos? count) @friends-of-followers))
          (is (re-find #"Retrying with alternate credentials" result-out)))))))


