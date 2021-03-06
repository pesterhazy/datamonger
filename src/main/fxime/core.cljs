(ns fxime.core
  (:require [clojure.core]
            [clojure.pprint]
            [clojure.string :as str]
            [cljs.reader :as reader]
            [cognitect.transit :as t]
            [datafrisk.core :as d]
            [goog.object :as gobj]
            [sci.core :as sci]
            [reagent.core :as r]
            ["react" :as react]
            ["gridjs-react" :as gridjs])
  (:import [goog.string StringBuffer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare xpr-ui)

(defn xpr-map-ui [v opts]
  (let [[expanded set-expanded] (react/useState (not (:collapsed-by-default opts)))
        toggle (fn [] (set-expanded not))]
    (-> [:span [:a {:on-click toggle} "{"]]
        (into (if expanded
                (->> v
                     (map (fn [[mk mv]]
                            [:span.pair [:span.k [xpr-ui mk opts]] " " [:span.v [xpr-ui mv opts]]]))
                     (interpose ", "))
                [[:a {:on-click toggle} "#_elided_" (count v)]]))
        (conj [:a {:on-click toggle} "}"]))))

(defn xpr-seq-ui [v opening closing opts]
  (let [[expanded set-expanded] (react/useState (not (:collapsed-by-default opts)))
        toggle (fn [] (set-expanded not))]
    (-> [:span [:a {:on-click toggle} opening]]
        (into (if expanded
                (->> v
                     (map (fn [vv]
                            [xpr-ui vv opts]))
                     (interpose " "))
                [[:a {:on-click toggle} "#_elided_" (count v)]]))
        (conj [:a {:on-click toggle} closing]))))

(defn xpr-ui [v opts]
  (cond
    (map? v)
    [:f> xpr-map-ui v opts]
    (vector? v)
    [:f> xpr-seq-ui v "[" "]" opts]
    (sequential? v)
    [:f> xpr-seq-ui v "(" ")" opts]
    :else
    [:span.leaf (pr-str v)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce !transit-reader
  (delay (t/reader :json)))

(defn read-transit [s]
  (t/read @!transit-reader s))

(defn err-boundary
  []
  (r/create-class
   {:display-name "err-boundary"
    :get-derived-state-from-error (fn [error]
                                    #js {:error error})
    :reagent-render (fn [& children]
                      (if-let [error (some-> (.-state (r/current-component)) .-error)]
                        [:pre [:code "Error while printing: " (pr-str error)]]
                        (into [:<>] children)))}))

;; TODO: table view with https://github.com/adazzle/react-data-grid

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- explode-step [path v]
  (cond
    (map? v)
    (->> v
         (mapcat (fn [[mk mv]]
                   (explode-step (conj path mk) mv))))
    (vector? v)
    (->> v
         (map vector (range))
         (mapcat (fn [[idx vv]]
                   (explode-step (conj path [:VEC idx]) vv))))
    :else
    [(conj path v)]))

(defn explode [v]
  (explode-step [] v))

(defn assoc-vec [ve idx v]
  (assert (vector? ve))
  (let [{:keys [last-seen cur mapping]} (meta ve)
        new-idx (cond
                  (and (some? last-seen) (< idx last-seen))
                  (throw (ex-info "Increasing idx expected" {:idx idx
                                                             :last-seen last-seen}))
                  (not cur)
                  0
                  (= idx last-seen)
                  cur
                  :else
                  (inc cur))]
    (-> (assoc ve new-idx v)
        (with-meta {:last-seen idx
                    :cur new-idx
                    :mapping (assoc mapping idx new-idx)}))))

(defn patch [m k v]
  ;; FIXME: avoid collision
  (if (and (vector? k) (= 2 (count k)) (= :VEC (first k)))
    (assoc-vec (or m []) (second k) v)
    (assoc m k v)))

(defn patch-in
  [m [k & ks :as xxx] v]
  (if ks
    (if (and (vector? k) (= 2 (count k)) (= :VEC (first k)))
      (let [{:keys [mapping]} (meta m)]
        (patch m k (patch-in (if (get mapping (second k))
                               (get m (get mapping (second k)))
                               nil)
                             ks
                             v)))
      (patch m k (patch-in (get m k) ks v)))
    (patch m k v)))

(defn implode [cs]
  (->> cs
       (reduce (fn [acc v]
                 (patch-in acc (pop v) (peek v)))
               nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn transform-clj [code v]
  (try
    (if (str/blank? code)
      v
      (sci/eval-string code {:bindings {'% v}}))
    (catch :default e
      (js/console.error e)
      {:error e})))

(defn transform-flat [s v]
  (try
    (if (str/blank? s)
      v
      (let [old (explode v)
            new (filter (fn [c]
                          (str/includes? (pr-str c) (or s "")))
                        old)]
        (with-meta (implode new)
          {:comment (str "matching " (count new) "/" (count old))})))
    (catch :default e
      (js/console.error e)
      {:error e})))

(defn display-ui [co v transform-fn code]
  (let [v* (transform-fn code v)]
    (when-let [comment (-> v* meta :comment)]
      [:div.comment comment])
    [co v*]))

(defn transform-ui [rinf co transform transform-fn v]
  (let [!el (atom nil)
        ls-key (str (name transform) ":" (or (-> rinf :route :path-params :fname)
                                             (-> rinf :route :path-params :id)))
        [dirty set-dirty] (react/useState false)
        [code set-code] (react/useState (js/localStorage.getItem ls-key))
        submit (fn [s]
                 (set-dirty false)
                 (js/localStorage.setItem ls-key s)
                 (set-code s))]
    [:div
     [:div {:style {:width 600}}
      [:textarea {:ref (fn [el] (reset! !el el))
                  :class (when dirty "dirty")
                  :style {:width 600 :height 200 :padding 6}
                  :default-value (or code "")
                  :on-change (fn [^js/Event e]
                               (set-dirty true))
                  :on-key-down (fn [^js/Event e]
                                 (when (and (= "Enter" (gobj/get e "key"))
                                            (or (gobj/get e "ctrlKey")
                                                (gobj/get e "metaKey")))
                                   (submit (-> e .-target .-value))
                                   (.preventDefault e)))}]
      [:div.mb
       [:a.click {:on-click (fn [] (submit (-> @!el .-value)))}
        "apply"]]]
     [err-boundary {:key code} ;; use code as key to clear error state
      [display-ui co v transform-fn code]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn preview-ui [v]
  (binding [clojure.core/*print-length* 3]
    [:code (pr-str v)]))

(defn pr-ui [v]
  [:code [xpr-ui v {:collapsed-by-default true}]])

(defn pprint-ui [v]
  [:pre (with-out-str (clojure.pprint/pprint v))])

(defn print-table-ui [v]
  (when-not (and (seq v) (map? (first v)))
    (throw "Unexpected datastructure"))
  [:pre (str/trim (with-out-str (clojure.pprint/print-table v)))])

(defn grid-ui [v]
  (when-not (and (seq v) (map? (first v)))
    (throw "Unexpected datastructure"))
  (let [fields
        (-> v first keys)
        data
        (->> v
             (map (fn [row]
                    (map (fn [field] (get row field)) fields))))
        columns
        (map name fields)]
    [:div.mr.ml
     [:> gridjs/Grid {:data data
                      :columns columns
                      :sort true
                      :search #js{:enabled true}}]]))

(defn interactive-ui [v]
  (d/DataFriskView v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def the-modes
  {:preview preview-ui
   :pprint pprint-ui
   :pr pr-ui
   :print-table print-table-ui
   :grid grid-ui
   :interactive interactive-ui})

(def the-transforms
  {:flat transform-flat
   :clj transform-clj})

(defn pick-ui [{:keys [xs x on-click]}]
  (->> (keys xs)
       (map (fn [k]
              [:li.menu-item
               [:a.click
                {:class (when (= k x) "selected")
                 :on-click #(on-click k)}
                (name k)]]))
       (into [:ul.menu])))

(defn header-ui [& children]
  [:div.header
   (into [:div] children)
   [:div (str "fxime-" (gobj/get js/window "fxime_version"))]])

(defn menu-ui [{:keys [rinf navigate-to]} v]
  (let [transform (or (some-> rinf :params :transform keyword)
                      (first (keys the-transforms)))]
    [:div
     [header-ui
      [:div.back [:a.click {:on-click (fn [] (navigate-to {:route {:name :root}}))}
                  "<< back"]]]
     [pick-ui {:xs the-transforms
               :x transform
               :on-click (fn [k]
                           (navigate-to (fn [rinf]
                                          (assoc-in rinf [:params :transform] (name k)))))}]
     (let [co (fn [v]
                (let [mode (or (some-> rinf :params :mode keyword)
                               (first (keys the-modes)))]
                  [:div
                   [pick-ui {:xs the-modes
                             :x mode
                             :on-click (fn [k]
                                         (navigate-to (fn [rinf]
                                                        (assoc-in rinf [:params :mode] (name k)))))}]

                   [err-boundary
                    [(or (the-modes mode) (throw "Unknown mode")) v]]]))]
       ^{:key (name transform)}
       [transform-ui rinf co transform (the-transforms transform) v])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-example+ [{:keys [kind fname]}]
  (js/Promise.resolve
   (-> (js/fetch (str "/9a57cb91-581d-4d51-a539-b656be2a0b69/static/example/"
                      (name kind)
                      "/"
                      fname))
       (.then (fn [r]
                (when-not (.-ok r)
                  (throw "Could not fetch"))
                r))
       (.then (fn [r]
                (case kind
                  :json
                  (-> (.json r)
                      (.then (fn [r]
                               (js->clj r :keywordize-keys true))))
                  :edn
                  (-> (.text r)
                      (.then (fn [r] (reader/read-string r))))
                  :transit
                  (-> (.text r)
                      (.then (fn [r] (read-transit r))))))))))

(defn load-blob+ [{:keys [kind id]}]
  (js/Promise.resolve
   (case kind
     :json
     (-> (js/localStorage.getItem id)
         js/JSON.parse
         (js->clj :keywordize-keys true))
     :edn
     (-> (js/localStorage.getItem id)
         reader/read-string))))

(defn url->rinf [url parse-fn]
  (let [[pathname search] (str/split url #"\?")
        params (js/URLSearchParams. (or search ""))]
    {:route (parse-fn pathname)
     :params (->> params
                  (map (fn [[k v]]
                         [(keyword k) v]))
                  (into {}))}))
(defn rinf->url [{:keys [route params]} unparse-fn]
  (let [pathname (unparse-fn route)]
    (str (str (if (str/starts-with? (or pathname "") "/")
                nil
                "/")
              pathname)
         (when (seq params)
           (str "?" (->> params
                         (map (fn [[k v]]
                                (str (name k) "=" (str v))))
                         (str/join "&")))))))

(defn pathname->route [pathname]
  (if-let [matches (re-matches #"^/app/example/(.*)/(.*)$" pathname)]
    {:name :example
     :path-params (-> (zipmap [:kind :fname] (rest matches))
                      (update :kind keyword))}
    (if-let [matches (re-matches #"^/(app/?)?$" pathname)]
      {:name :root
       :path-params {}}
      (if-let [matches (re-matches #"^/app/blob/(.*)/(.*)$" pathname)]
        {:name :blob
         :path-params (-> (zipmap [:kind :id] (rest matches))
                          (update :kind keyword))}
        nil))))

(defn route->pathname [{route-name :name, :keys [path-params]}]
  (case route-name
    :root
    "/app"
    :example
    (str "/app/example/"
         (name (:kind path-params))
         "/"
         (:fname path-params))
    :blob
    (str "/app/blob/"
         (name (:kind path-params))
         "/"
         (:id path-params))))

(defn get-rinf []
  (url->rinf (str js/location.pathname js/location.search)
             pathname->route))

(def the-examples
  [{:kind :json :fname "widget.json"}
   {:kind :json :fname "package.json"}
   {:kind :json :fname "countries.json"}
   {:kind :edn :fname "shadow-cljs.edn"}
   {:kind :transit :fname "angels.transit"}])

(defn select-ui [{:keys [navigate-to]}]
  [:div
   [header-ui]
   (->> the-examples
        (map (fn [path-params]
               [:div
                [:a.click
                 {:on-click
                  (fn []
                    (navigate-to {:route {:name :example
                                          :path-params path-params}}))}
                 (:fname path-params)]]))
        (into [:div]))])

(defn load-ui [{:keys [rinf] :as ctx} load+]
  (let [[v update-v] (react/useState nil)]
    (react/useEffect
     (fn []
       (-> (load+ (route->pathname (:route rinf)))
           (.then (fn [result]
                    (update-v result))))
       js/undefined)
     #js[])
    (when v
      [menu-ui ctx v])))

(defn route-ui [ctx]
  (case (-> ctx :rinf :route :name)
    :root
    [select-ui ctx]
    :example
    [load-ui ctx (fn [] (load-example+ (-> ctx :rinf :route :path-params)))]
    :blob
    [load-ui ctx (fn [] (load-blob+ (-> ctx :rinf :route :path-params)))]
    nil
    [:div "Route not found"]))

(defn router-ui []
  (let [[rinf set-rinf] (react/useState (get-rinf))
        ctx {:rinf rinf
             :navigate-to set-rinf}
        new-url (rinf->url rinf route->pathname)
        handle-change (fn [] (set-rinf (get-rinf)))]
    (react/useEffect (fn []
                       (js/history.pushState {} nil (str js/location.origin new-url))
                       js/undefined)
                     #js[new-url])
    (react/useEffect (fn []
                       (js/window.addEventListener "popstate" handle-change)
                       (fn []
                         (js/window.removeEventListener "popstate" handle-change)))
                     #js[handle-change])
    [route-ui ctx]))

;; FIXME: add /app
(defn init []
  (when-let [[_ kind] (re-matches #"^/app/from-hash/(json|edn)$" js/location.pathname)]
    (let [params (js/URLSearchParams. (-> js/location.hash
                                          (str/replace #"^#" "")))
          data (.get params "data")
          id (random-uuid)]
      (js/localStorage.setItem (str id) data)
      (js/history.pushState {} nil (str js/location.origin
                                        "/app/blob/"
                                        kind
                                        "/"
                                        id)))))
