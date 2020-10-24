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

(defn- explode-step [path v]
  (cond
    (map? v)
    (->> v
         (mapcat (fn [[mk mv]]
                   (explode-step (conj path mk) mv))))
    (vector? v)
    (->> v
         (map vector (range))
         (mapcat (fn [[i vv]]
                   (explode-step (conj path i) vv))))
    :else
    [(conj path v)]))

(defn explode [v]
  (explode-step [] v))

(defn implode [cs]
  cs)

(deftest t-explode
  (let [v {:foo {:bar 1
                 :baz 2}
           :quux 3}]
    (is (= [[:foo :bar 1]
            [:foo :baz 2]
            [:quux 3]]
           (-> v explode))))
  (let [v [:a :b]]
    (is (= [[0 :a] [1 :b]]
           (-> v explode)))))

#_(deftest implode-explode
    (let [v {:foo {:bar 1}}]
      (is (= v (-> v explode implode)))))
