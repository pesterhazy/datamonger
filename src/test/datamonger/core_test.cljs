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
    (is (= [[[:VEC 0] :a] [[:VEC 1] :b]]
           (-> v x/explode)))))

(deftest t-patch-in
  (let [c [:foo :bar 1]]
    (is (= {:foo {:bar 1}}
           (x/patch-in nil (pop c) (peek c)))))
  (let [c [:foo [:VEC 0] 100]]
    (is (= {:foo [100]}
           (x/patch-in nil (pop c) (peek c))))))

(deftest implode-explode
  (let [v {:foo {:bar [:a :b]}}]
    (is (= v (x/implode (x/explode v))) "maps"))
  (let [v [[:a :b]]]
    (is (= v (x/implode #pp (x/explode v))) "vector")))

(def test-v
  '{:source-paths
    ["src/dev"
     "src/main"
     "src/test"]

    :dependencies
    [[reagent "1.0.0-alpha2"]
     [data-frisk-reagent "0.4.5"]
     [borkdude/sci "0.1.1-alpha.7"]]

    :dev-http
    {8080 "public"}

    :builds
    {:app {:target :browser
           :output-dir "public/js"
           :asset-path "/js"
           :modules {:main {:entries [datamonger.app]
                            :init-fn datamonger.app/init}}}
     :test {:target    :browser-test
            :test-dir  "test-public"
            :ns-regexp "-test$"
            :devtools  {:http-port 8021
                        :http-root "test-public"}}}})

#_(deftest t-complex
    (is (= #pp test-v #pp (x/implode (x/explode test-v)))))
