(ns datamonger.core-test
  (:require [clojure.test :as t]
            [datamonger.core :as x]))

(defn roundtrip [s]
  (t/is (= s (-> s (x/url->opts) (x/opts->url)))))

(t/deftest t1
  (roundtrip "/")
  (roundtrip "/foo")
  (roundtrip "/foo?a=b&c=d")
  (t/is (= {:pathname "foo", :params {:a "b", :c "d"}} (x/url->opts "foo?a=b&c=d"))))
