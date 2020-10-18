(ns datamonger.core
  (:require [clojure.core]
            [clojure.string :as str]
            [datafrisk.core :as d]
            [goog.object :as gobj]
            ["react" :as react]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
     [:div.back [:a.click {:on-click (fn [] (set-opts {}))} "<< back"]]
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

(defn url->opts [url]
  (let [[pathname search] (str/split url #"\?")
        params (js/URLSearchParams. (or search ""))]
    {:pathname pathname
     :params (->> params
                  (map (fn [[k v :as xxx]]
                         [(keyword k) v]))
                  (into {}))}))
(defn opts->url [{:keys [pathname params]}]
  (str (str (if (str/starts-with? (or pathname "") "/")
              nil
              "/")
            pathname)
       (when (seq params)
         (str "?" (->> params
                       (map (fn [[k v]]
                              (str (name k) "=" (str v))))
                       (str/join "&"))))))

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
  (let [[opts set-opts] (react/useState (url->opts (str js/location.pathname js/location.search)))
        ctx {:opts opts :set-opts set-opts}
        new-url (opts->url opts)]
    (react/useEffect (fn []
                       (prn [:new-url new-url])
                       (js/history.pushState {} nil (str js/location.origin new-url))
                       js/undefined)
                     #js[new-url])
    (if (and (seq (:pathname opts))
             (not= "/" (:pathname opts)))
      [load-ui ctx]
      [select-ui ctx])))
