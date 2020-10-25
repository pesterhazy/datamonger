(ns datamonger.core-test
  (:require [clojure.test :refer [is deftest]]
            [datamonger.core :as x]))

(defn roundtrip [s]
  (is (= s (-> s
               (x/url->rinf x/pathname->route)
               (x/rinf->url x/route->pathname)))))

(deftest t1
  (roundtrip "/")
  (roundtrip "/examples/json/widget.json")
  (roundtrip "/blob/json/20d26b12-3cfb-48f1-bacc-b5f0eac870a4"))

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
    (is (= v (x/implode (x/explode v))) "vector")))

(def complex-v
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

(deftest implode-explode-complex
  (is (= complex-v (x/implode (x/explode complex-v))) "vector"))

(deftest t-partial
  (is (= [:a :b :c]
         (x/implode [[[:VEC 0] :a]
                     [[:VEC 2] :b]
                     [[:VEC 4] :c]])))
  (is (= [{:a 100 :b 101}]
         (x/implode [[[:VEC 1] :a 100]
                     [[:VEC 1] :b 101]]))))

(def complex-partial-cs
  [[[:VEC 1] :demonyms :eng :f "Afghan"]
   [[:VEC 1] :demonyms :eng :m "Afghan"]
   [[:VEC 1] :demonyms :fra :f "Afghane"]
   [[:VEC 1] :demonyms :fra :m "Afghan"]
   [[:VEC 1] :name :common "Afghanistan"]
   [[:VEC 1] :name :official "Islamic Republic of Afghanistan"]
   [[:VEC 1] :currencies :AFN :name "Afghan afghani"]
   [[:VEC 1]
    :translations
    :ita
    :official
    "Repubblica islamica dell'Afghanistan"]
   [[:VEC 1] :translations :ita :common "Afghanistan"]
   [[:VEC 1]
    :translations
    :fra
    :official
    "République islamique d'Afghanistan"]
   [[:VEC 1] :translations :fra :common "Afghanistan"]
   [[:VEC 1]
    :translations
    :deu
    :official
    "Islamische Republik Afghanistan"]
   [[:VEC 1] :translations :deu :common "Afghanistan"]
   [[:VEC 1]
    :translations
    :nld
    :official
    "Islamitische Republiek Afghanistan"]
   [[:VEC 1] :translations :nld :common "Afghanistan"]])

#_(deftest t-complex-partial
    (is (= :???
           (x/implode complex-partial-cs))))
