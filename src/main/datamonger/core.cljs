(ns datamonger.core
  (:require [reagent.core :as r]
            [reagent.dom :as rd]))

(defn load+ []
  (-> (js/fetch "/examples/countries.json")
      (.then (fn [r]
               (.json r)))))

(defn main-ui []
  [:h1 "hello"])

(rd/render [main-ui] (js/document.getElementById "app"))

(prn ::hello)
(-> (load+)
    (.then (fn [v]
             (js/console.log v))))
