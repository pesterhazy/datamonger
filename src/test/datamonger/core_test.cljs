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

(defn patch [m k v]
  ;; FIXME: avoid collision
  (if (= :CONJV k)
    (conj (or m []) v)
    (assoc m k v)))

(defn patch-in
  [m [k & ks] v]
  (if ks
    (patch m k (patch-in (get m k) ks v))
    (patch m k v)))

(defn implode [cs]
  (->> cs
       (reduce (fn [acc c]
                 (assert (vector? c))
                 nil
                 #_(apply-c acc))
               nil)))

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

(deftest t-patch-in
  (let [c [:foo :bar 1]]
    (is (= {:foo {:bar 1}}
           (patch-in nil (pop c) (peek c)))))
  (let [c [:foo :CONJV 100]]
    (is (= {:foo [100]}
           (patch-in nil (pop c) (peek c))))))

#_(deftest implode-explode
    (let [v {:foo {:bar 1}}]
      (is (= v (-> v explode implode)))))
