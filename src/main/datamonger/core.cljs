(ns datamonger.core
  (:require [clojure.core]
            [clojure.string :as str]
            [datafrisk.core :as d]
            [reagent.core :as r]
            [reagent.dom :as rd]
            ["react" :as react]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; FIXME: no hash

(defn preview-ui [v]
  (binding [clojure.core/*print-length* 3]
    [:div (pr-str v)]))

(defn interactive-ui [v]
  (d/DataFriskView v))

(def the-modes [:preview :interactive])

(defn menu-ui [opts v]
  (let [[mode set-mode] (react/useState (or (some-> opts :params :mode keyword)
                                            (first the-modes)))]
    [:div
     (->> the-modes
          (map (fn [k]
                 [:li.menu-item
                  {:class (when (= k mode) "selected")}
                  [:a {:href (str "#?mode=" (name k))
                       :on-click (fn [] (set-mode k))} (name k)]]))
          (into [:ul.menu]))
     (case mode
       :preview [preview-ui v]
       :interactive [interactive-ui v])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load+ [fname]
  (-> (js/fetch (str "/examples/" fname))
      (.then (fn [r]
               (.json r)))
      (.then (fn [r]
               (js->clj r)))))

(defn hash->opts [hash]
  (let [[path search] (str/split (str/replace hash #"^#" "") #"\?")
        params (js/URLSearchParams. search)]
    {:path path
     :params (->> params
                  (map (fn [[k v]]
                         [(keyword k) v]))
                  (into {}))}))

(defn select-ui [opts]
  [:div
   [:div
    [:a {:href "#widget.json"} "widget.json"]]
   [:div
    [:a {:href "#countries.json"} "countries.json"]]])

(defn load-ui [opts]
  (let [[v update-v] (react/useState nil)]
    (assert (seq (:path opts)))
    (prn [::load-ui {:loaded? (boolean v)}])
    (react/useEffect
     (fn []
       (-> (load+ (:path opts))
           (.then (fn [result]
                    (update-v result))))
       js/undefined)
     #js[])
    (if v
      [menu-ui opts v]
      [:div])))

(defn main-ui []
  (let [opts (hash->opts js/location.hash)]
    (prn [::main-ui opts])
    (if (seq (:path opts))
      [load-ui opts]
      [select-ui opts])))

(def functional-compiler (r/create-compiler {:function-components true}))

(defn init []
  (rd/render [main-ui]
             (js/document.getElementById "app")
             functional-compiler))

(init)
