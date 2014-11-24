(ns gg-detector.identification-test
  (:require [clojure.test :refer :all]
            [gg-detector.identification :refer :all]))

(def test-a #{:foo :bar :baz})
(def test-b #{:a :b :c :d})
(def test-c #{:x :y :z :d})

(def good-user
  {:a #{:foo :butts :ghost}
   :b #{:a :delta :bogart}
   :c #{:space :is :the :place}})

(def bad-user
  {:a #{:foo :butts :baseball}
   :b #{:a :b :x :z}
   :c #{:x :y :z :shut :up}})

(deftest score-test
  (testing "provides rational scores"
    (is (= 1/3 (score :a test-a good-user)))
    (is (= 3/4 (score :c test-c bad-user)))))

(deftest test-identify->*
  (let [test-pairs [[:a test-a] [:b test-b] [:c test-c]]]
    (testing "returns a cumulative score if no limit is set"
      (is (= 7/12
             (identify->* test-pairs good-user)))
      (is (= 12/9
             (identify->* test-pairs bad-user))))
    (testing "otherwise returns the first cumulative score over the limit"
      (is (= 5/6
             (identify->* test-pairs bad-user 0.8))))))

