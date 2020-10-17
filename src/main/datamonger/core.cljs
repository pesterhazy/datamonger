(ns datamonger.core
  (:require [clojure.core]
            [datafrisk.core :as d]
            [reagent.core :as r]
            [reagent.dom :as rd]
            ["react" :as react]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn view-ui [v]
  (d/DataFriskView v)
  #_(binding [clojure.core/*print-length* 3]
      [:div (pr-str v)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load+ []
  (-> (js/fetch "/examples/countries.json")
      (.then (fn [r]
               (.json r)))
      (.then (fn [r]
               (js->clj r)))))

(defn main-ui []
  (let [[v update-v] (react/useState nil)]
    (react/useEffect
     (fn []
       (-> (load+)
           (.then (fn [result]
                    (update-v result))))
       js/undefined)
     #js[])
    (if v
      [view-ui v]
      [:div])))

(def functional-compiler (r/create-compiler {:function-components true}))

(defn init []
  (rd/render [main-ui]
             (js/document.getElementById "app")
             functional-compiler))

(init)
