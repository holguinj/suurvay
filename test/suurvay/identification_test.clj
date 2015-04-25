(ns suurvay.identification-test
  (:require [clojure.test :refer :all]
            [schema.test :refer [validate-schemas]]
            [schema.core :as sc]
            [suurvay.schema :refer [Status User]]
            [suurvay.storage :as st]
            [suurvay.storage.in-memory :refer [in-memory-only]]
            [suurvay.twitter-rest :as t]
            [suurvay.twitter-rest-test :refer [test-creds]]
            [suurvay.identification :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup and data
(declare test-status)

(use-fixtures :once validate-schemas)

(def twitter-creds
  (twitter-api test-creds))

(def pure-test-map
  {:limit 10
   :before #(do (is (sc/validate Status %))
                (is (= test-status %)) 1)
   :real-name #(do (is (= "Twitter API (mock)" %)) 1)
   :profile #(do (is (= (:user test-status) %)) 1)})

(def validator-test-map
  {:limit 10
   :before #(do (is (sc/validate Status %)) 1)
   :real-name #(do (is (sc/validate sc/Str %)) 1)
   :profile #(do (is (sc/validate User %)) 1)
   :timeline #(do (is (sc/validate [Status] %))
                  (is (= 200 (count %))) 1)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
(deftest pure-test
  (testing "ID functions are called with available data, sparing API calls if possible"
    (is (= 3 (score-user twitter-creds pure-test-map test-status))))

  (testing "tests complete early if the limit is hit"
    (let [test-map (assoc pure-test-map :limit 1)]
      (is (= 1 (score-user twitter-creds test-map test-status)))))

  (testing "tests complete immediately if the output is ever negative"
    (let [test-map (assoc pure-test-map :profile (constantly -3))]
      (is (= -1 (score-user twitter-creds test-map test-status))))))

(deftest with-api-test
  (testing "the :timeline, :friends, and :followers tests require API access"
    (let [api-tests {:timeline #(do (is (sc/validate [Status] %))
                                    (is (= 200 (count %))) 1)
                     :friends #(do (is (sc/validate [sc/Int] %))
                                   (is (pos? (count %))) 1)
                     :followers #(do (is (sc/validate [sc/Int] %))
                                     (is (pos? (count %))) 1)}
          complete-test-map (merge pure-test-map api-tests)]
      (is (= 6 (score-user twitter-creds complete-test-map test-status))))))

(def mock-twitter
  (reify TwitterAPI
    (get-name [this _] "Real Name")
    (get-profile [this _] "This is my profile.")
    (get-timeline-details [this _] [])
    (get-friends [this _] [123 456 789])
    (get-followers [this _] [987 654 321])))

(deftest mocked-twitter-data-test
  (testing "The protocol methods are called by the scoring functions"
    (let [test-map {:limit 10
                    :real-name #(if (= "Real-Name" %) 1 0)
                    :get-profile #(if (= "This is my profile." %) 1 0)
                    :timeline #(if (= [] %) 1 0)
                    :friends #(if (= [123 456 789] %) 1 0)
                    :followers #(if (= [987 654 321] %) 1 0)}]
      (= 5 (score-user mock-twitter test-map {})))))

(deftest storage-backed-test
  (testing "When given a storage-backed TwitterAPI object"
    (let [stg (in-memory-only)
          stg-api (twitter-api-with-storage test-creds stg)
          timeline (t/get-timeline-details test-creds "postpunkjustin")
          user (t/get-user test-creds "postpunkjustin")
          ;; construct a suitably detailed status object. this is
          ;; weird because we normally get status objects from the
          ;; streaming API, where they come with a very detailed :user
          ;; key. In this case, pulling the statuses from
          ;; get-timeline-details is not sufficient and we have to add
          ;; a full user object to the status like so:
          status (assoc (first timeline) :user user)]

      (testing "the inputs are still validated"
        (is (= 4 (score-user stg-api validator-test-map status))))

      (testing "the results are stored in the storage object"
        (let [id (t/get-id test-creds status)
              tweet-id (:id status)]
          (is (sc/validate User (st/get-user stg id)))
          (is (sc/validate Status (st/get-status stg tweet-id)))
          (is (sc/validate [Status] (st/get-timeline stg id))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Example data
(def test-status
  {:in_reply_to_screen_name nil,
   :coordinates nil,
   :in_reply_to_status_id_str nil,
   :place nil,
   :possibly_sensitive false,
   :geo nil,
   :in_reply_to_status_id nil,
   :entities
   {:hashtags [],
    :symbols [],
    :user_mentions [],
    :urls
    [{:url "https://t.co/Fk6AlAHKXw",
      :expanded_url
      "https://twittercommunity.com/t/deprecation-of-account-update-profile-colors/28692",
      :display_url "twittercommunity.com/t/deprecation-…",
      :indices [58 81]}]},
   :source
   "<a href=\"http://itunes.apple.com/us/app/twitter/id409789998?mt=12\" rel=\"nofollow\">Twitter for Mac</a>",
   :lang "en",
   :in_reply_to_user_id_str nil,
   :id 540647748186681344,
   :contributors nil,
   :truncated false,
   :retweeted false,
   :in_reply_to_user_id nil,
   :id_str "540647748186681344",
   :favorited false,
   :user {:description
          "The Real Twitter API. I tweet about API changes, service issues and happily answer questions about Twitter and our API. Don't get an answer? It's on my website.",
          :profile_link_color "0084B4",
          :profile_sidebar_border_color "C0DEED",
          :is_translation_enabled false,
          :profile_image_url
          "http://pbs.twimg.com/profile_images/2284174872/7df3h38zabcvjylnyfe3_normal.png",
          :profile_use_background_image true,
          :default_profile false,
          :profile_background_image_url
          "http://pbs.twimg.com/profile_background_images/656927849/miyt9dpjz77sc0w3d4vj.png",
          :is_translator false,
          :profile_text_color "333333",
          :profile_banner_url
          "https://pbs.twimg.com/profile_banners/6253282/1347394302",
          :profile_location nil,
          :name "Twitter API (mock)",
          :profile_background_image_url_https
          "https://pbs.twimg.com/profile_background_images/656927849/miyt9dpjz77sc0w3d4vj.png",
          :favourites_count 27,
          :screen_name "twitterapi",
          :entities
          {:url
           {:urls
            [{:url "http://t.co/78pYTvWfJd",
              :expanded_url "http://dev.twitter.com",
              :display_url "dev.twitter.com",
              :indices [0 22]}]},
           :description {:urls []}},
          :listed_count 12870,
          :profile_image_url_https
          "https://pbs.twimg.com/profile_images/2284174872/7df3h38zabcvjylnyfe3_normal.png",
          :statuses_count 3525,
          :contributors_enabled false,
          :following true,
          :lang "en",
          :utc_offset -28800,
          :notifications false,
          :default_profile_image false,
          :profile_background_color "C0DEED",
          :id 6253282,
          :follow_request_sent false,
          :url "http://t.co/78pYTvWfJd",
          :time_zone "Pacific Time (US & Canada)",
          :profile_sidebar_fill_color "DDEEF6",
          :protected false,
          :profile_background_tile true,
          :id_str "6253282",
          :geo_enabled true,
          :location "San Francisco, CA",
          :followers_count 2570684,
          :friends_count 48,
          :verified true,
          :created_at "Wed May 23 06:01:13 +0000 2007"}
   :retweet_count 60,
   :favorite_count 42,
   :created_at "Thu Dec 04 23:24:02 +0000 2014",
   :text
   "Weeeeeeee this is a fun example tweet."})

