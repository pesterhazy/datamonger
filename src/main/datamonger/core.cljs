(ns datamonger.core)

(defn load+ []
  (-> (js/fetch "/examples/countries.json")
      (.then (fn [r]
               (.json r)))))

(prn ::hello)
(-> (load+)
    (.then (fn [v]
             (js/console.log v))))
