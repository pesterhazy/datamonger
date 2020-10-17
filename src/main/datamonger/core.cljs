(ns datamonger.core
  (:require [clojure.core]
            [clojure.string :as str]
            [datafrisk.core :as d]
            [goog.object :as gobj]
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

(defn menu-ui [{:keys [opts set-opts]} v]
  (let [mode (or (some-> opts :params :mode keyword)
                 (first the-modes))]
    [:div
     (->> the-modes
          (map (fn [k]
                 [:li.menu-item
                  {:class (when (= k mode) "selected")}
                  [:a.click {:on-click (fn [] (set-opts (fn [opts] (assoc-in opts [:params :mode] (name k)))))} (name k)]]))
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

(defn hash->opts [{:keys [pathname search]}]
  (let [params (js/URLSearchParams. (or (-> search (str/replace #"^\?" "")) ""))]
    {:pathname pathname
     :params (->> params
                  (map (fn [[k v :as xxx]]
                         [(keyword k) v]))
                  (into {}))}))
;; FIXME: rename
(defn opts->hash [{:keys [pathname params]}]
  {:pathname pathname
   :search (when (seq params)
             (str "?" (->> params
                           (map (fn [[k v]]
                                  (str (name k) "=" (str v))))
                           (str/join "&"))))})

(defn select-ui [{:keys [set-opts]}]
  [:div
   [:div
    [:a.click {:on-click (fn [] (set-opts (fn [opts] (assoc opts :pathname "widget.json"))))}
     "widget.json"]]
   [:div
    [:a.click {:on-click (fn [] (set-opts (fn [opts] (assoc opts :pathname "countries.json"))))}
     "countries.json"]]])

(defn load-ui [{:keys [opts] :as ctx}]
  (let [[v update-v] (react/useState nil)]
    (prn [::load-ui {:loaded? (boolean v)}])
    (react/useEffect
     (fn []
       (-> (load+ (:pathname opts))
           (.then (fn [result]
                    (update-v result))))
       js/undefined)
     #js[])
    (if v
      [menu-ui ctx v]
      [:div])))

(defn main-ui []
  ;; FIXME: prefix with /app
  (let [[opts set-opts] (react/useState (hash->opts {:pathname js/location.pathname
                                                     :search js/location.search}))
        ctx {:opts opts :set-opts set-opts}
        new-hash (opts->hash opts)]
    (react/useEffect (fn []
                       (js/history.pushState {} nil (str (:pathname new-hash) (when (:search new-hash) (str "?") (:search new-hash))))
                       js/undefined)
                     #js[new-hash])
    (prn [::main-ui opts])
    (if (and (seq (:pathname opts))
             (not= "/" (:pathname opts)))
      [load-ui ctx]
      [select-ui ctx])))

(def functional-compiler (r/create-compiler {:function-components true}))

(defn init []
  (rd/render [main-ui]
             (js/document.getElementById "app")
             functional-compiler))
