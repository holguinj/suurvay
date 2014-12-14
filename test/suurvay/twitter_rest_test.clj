(ns suurvay.twitter-rest-test
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [schema.test :refer [validate-schemas]]
            [environ.core :refer [env]]
            [suurvay.twitter-rest :refer :all]
            [suurvay.schema :as ssc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration and setup
(def test-creds
  "Reads and prepares Twitter API credentials from the environment using
  environ. :test-twitter-creds should be a map with the following
  keys only:

  :consumer-key
  :consumer-secret
  :access-token
  :access-secret"
  (let [creds (-> :test-twitter-creds env vals)]
    (apply make-ouauth-creds creds)))

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
  (testing "can request a profile"
    (let [profile (get-profile "twitter")]
      (is (re-find #"Twitter" (:description profile)))))

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

(deftest screen-name->id->name-test
  (testing "can convert screen-name -> id -> name"
    (let [ids (names->ids ["twitter" "twitterapi"])
          names (ids->names ids)]
      (is (= #{"Twitter" "Twitter API"}
             (set names)))
      (is (= #{783214 6253282}
             (set ids))))))
