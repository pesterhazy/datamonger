(ns datamonger.core-test
  (:require [clojure.test :refer [is deftest]]
            [datamonger.core :as x]))

(defn roundtrip [s]
  (is (= s (-> s (x/url->opts) (x/opts->url)))))

(deftest t1
  (roundtrip "/")
  (roundtrip "/foo")
  (roundtrip "/foo?a=b&c=d")
  (is (= {:pathname "foo", :params {:a "b", :c "d"}} (x/url->opts "foo?a=b&c=d"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest t-explode
  (let [v {:foo {:bar 1
                 :baz 2}
           :quux 3}]
    (is (= [[:foo :bar 1]
            [:foo :baz 2]
            [:quux 3]]
           (-> v x/explode))))
  (let [v [:a :b]]
    (is (= [[:CONJV :a] [:CONJV :b]]
           (-> v x/explode)))))

(deftest t-patch-in
  (let [c [:foo :bar 1]]
    (is (= {:foo {:bar 1}}
           (x/patch-in nil (pop c) (peek c)))))
  (let [c [:foo :CONJV 100]]
    (is (= {:foo [100]}
           (x/patch-in nil (pop c) (peek c))))))

(deftest implode-explode
  (let [v {:foo {:bar [:a :b]}}]
    (is (= v (x/implode (x/explode v))))))
