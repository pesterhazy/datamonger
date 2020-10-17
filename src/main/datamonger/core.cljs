(ns datamonger.core
  (:require [clojure.core]
            [reagent.core :as r]
            [reagent.dom :as rd]
            ["react" :as react]))

(defn load+ []
  (-> (js/fetch "/examples/countries.json")
      (.then (fn [r]
               (.json r)))))

(defn main-ui []
  (prn ::render)
  (let [[v update-v] (react/useState nil)]
    (react/useEffect
     (fn []
       (-> (load+)
           (.then (fn [result]
                    (prn (type update-v))
                    (update-v result))))
       js/undefined)
     #js[])
    (if v
      (binding [clojure.core/*print-length* 3]
        [:div (pr-str v)])
      [:div "loading..."])))

(def functional-compiler (r/create-compiler {:function-components true}))

(defn init []
  (rd/render [main-ui]
             (js/document.getElementById "app")
             functional-compiler))

(init)
