(ns datamonger.core
  (:require [clojure.core]
            [datafrisk.core :as d]
            [reagent.core :as r]
            [reagent.dom :as rd]
            ["react" :as react]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn simple-ui [v]
  (binding [clojure.core/*print-length* 3]
    [:div (pr-str v)]))

(defn interactive-ui [v]
  (d/DataFriskView v))

(defn view-ui [v]
  (let [[mode set-mode] (react/useState :simple)]
    [:div
     (->> [:simple :interactive]
          (map (fn [k]
                 [:li.menu-item
                  {:class (when (= k mode) "selected")}
                  [:a {:href "#"
                       :on-click (fn [] (set-mode k))} (name k)]]))
          (into [:ul.menu]))
     (case mode
       :simple [simple-ui v]
       :interactive [interactive-ui v])]))

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
