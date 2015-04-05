(ns suurvay.twitter-pure-test
  (:require [suurvay.twitter-pure :refer :all]
            [suurvay.schema :refer :all]
            [schema.core :as sc]
            [schema.test :refer [validate-schemas]]
            [clojure.test :refer :all]))

(use-fixtures :once validate-schemas)

;; TODO: move these helpers to their own ns
(sc/defn rand-user :- User
  []
  (let [id (rand-int 1e8)]
    {:id id
     :screen_name (str "user_" id)
     :description (str "Just a user who loves the number " id ".")
     :name (str (rand-nth ["Mr. " "Ms. "]) id)}))

(sc/defn rand-status :- Status
  []
  {:id (rand-int 999999)
   :text (str "Yes, I can count to " (rand-int 1e7) ".")
   :favorited (rand-nth [true false])
   :retweeted false
   :user (rand-user)})

(deftest get-id-test
  (testing "get-id"
    (testing "should return the user id"
      (testing "when given a user id (i.e., an integer)"
        (is (= 1234567 (get-id 1234567))))
      (testing "when given a user object"
        (let [{:keys [id] :as user} (rand-user)]
          (is (= id (get-id user)))))
      (testing "when given a status object"
        (let [{:keys [id] :as user} (rand-user)
              status (-> (rand-status)
                       (assoc :user user))]
          (is (= id (get-id status))))))
    (testing "should return nil when given a string"
      (is (nil? (get-id "not_an_id"))))))
