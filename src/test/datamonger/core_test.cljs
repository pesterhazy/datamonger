(ns datamonger.core-test
  (:require [clojure.test :as t]
            [datamonger.core :as x]))

(defn roundtrip [s]
  (t/is (= s (-> s (x/hash->opts) (x/opts->hash)))))

(t/deftest t1
  (roundtrip "#")
  (roundtrip "#foo")
  (roundtrip "#foo?a=b&c=d")
  (t/is (= {:path "foo", :params {:a "b", :c "d"}} (x/hash->opts "#foo?a=b&c=d"))))
