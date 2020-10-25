(ns datamonger.core
  (:require [clojure.core]
            [clojure.pprint]
            [clojure.string :as str]
            [cljs.reader :as reader]
            [datafrisk.core :as d]
            [goog.object :as gobj]
            [sci.core :as sci]
            ["react" :as react]))

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

(defn transform-ui [rinf co transform transform-fn v]
  (let [!el (atom nil)
        ls-key (str (name transform) ":"(-> rinf :pathname))
        [dirty set-dirty] (react/useState false)
        [code set-code] (react/useState (js/localStorage.getItem ls-key))
        submit (fn [s]
                 (set-dirty false)
                 (js/localStorage.setItem ls-key s)
                 (set-code s))
        v* (transform-fn code v)]
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
     (when-let [comment (-> v* meta :comment)]
       [:div.comment comment])
     [co v*]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn preview-ui [v]
  (binding [clojure.core/*print-length* 3]
    [:pre.pprint (pr-str v)]))

(defn pprint-ui [v]
  [:pre.pprint (with-out-str (clojure.pprint/pprint v))])

(defn interactive-ui [v]
  (d/DataFriskView v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def the-modes
  {:preview preview-ui
   :pprint pprint-ui
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

(defn menu-ui [{:keys [rinf set-rinf]} v]
  (let [transform (or (some-> rinf :params :transform keyword)
                      (first (keys the-transforms)))]
    [:div
     [:div.back [:a.click {:on-click (fn [] (set-rinf {:pathname "/"}))} "<< back"]]
     [pick-ui {:xs the-transforms
               :x transform
               :on-click (fn [k]
                           (set-rinf (fn [rinf]
                                       (assoc-in rinf [:params :transform] (name k)))))}]
     (let [co (fn [v]
                (let [mode (or (some-> rinf :params :mode keyword)
                               (first (keys the-modes)))]
                  [:div
                   [pick-ui {:xs the-modes
                             :x mode
                             :on-click (fn [k]
                                         (set-rinf (fn [rinf]
                                                     (assoc-in rinf [:params :mode] (name k)))))}]
                   [(or (the-modes mode) (throw "Unknown mode")) v]]))]
       ^{:key (name transform)}
       [transform-ui rinf co transform (the-transforms transform) v])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load+ [path]
  (js/Promise.resolve
   (if-let [[_ kind id] (some-> (re-matches #"^/blob/(.*)/(.*)$" path))]
     (case kind
       "json"
       (-> (js/localStorage.getItem id)
           js/JSON.parse
           (js->clj :keywordize-keys true))
       "edn"
       (-> (js/localStorage.getItem id)
           reader/read-string))
     (if-let [[_ kind] (re-matches #"^/examples/(.*)/.*$" path)]
       (-> (js/fetch (str "/static" path))
           (.then (fn [r]
                    (when-not (.-ok r)
                      (throw "Could not fetch"))
                    r))
           (.then (fn [r]
                    (case kind
                      "json"
                      (-> (.json r)
                          (.then (fn [r]
                                   (js->clj r :keywordize-keys true))))
                      "edn"
                      (-> (.text r)
                          (.then (fn [r] (reader/read-string r))))))))
       nil))))

(defn url->rinf [url parse-fn]
  (let [[pathname search] (str/split url #"\?")
        params (js/URLSearchParams. (or search ""))]
    {:pathname pathname
     :route (parse-fn pathname)
     :params (->> params
                  (map (fn [[k v]]
                         [(keyword k) v]))
                  (into {}))}))
(defn rinf->url [{:keys [pathname params]}]
  (str (str (if (str/starts-with? (or pathname "") "/")
              nil
              "/")
            pathname)
       (when (seq params)
         (str "?" (->> params
                       (map (fn [[k v]]
                              (str (name k) "=" (str v))))
                       (str/join "&"))))))

(defn parse-pathname [pathname]
  (if-let [matches (re-matches #"^/examples/(.*)/(.*)$" pathname)]
    {:name :example
     :path-params (zipmap [:kind :fname](rest matches))}
    (if-let [matches (re-matches #"^/$" pathname)]
      {:name :root
       :path-params {}}
      nil)))

(defn get-rinf []
  (url->rinf (str js/location.pathname js/location.search)
             parse-pathname))

(defn select-ui [{:keys [set-rinf]}]
  (->> ["/examples/json/widget.json"
        "/examples/json/countries.json"
        "/examples/json/package.json"
        "/examples/edn/shadow-cljs.edn"
        "/examples/edn/presentation.edn"]
       (map (fn [fname]
              [:div
               [:a.click
                {:on-click
                 (fn [] (set-rinf (fn [rinf] (assoc rinf :pathname fname))))}
                fname]]))
       (into [:div])))

(defn load-ui [{:keys [rinf] :as ctx}]
  (let [[v update-v] (react/useState nil)]
    (react/useEffect
     (fn []
       (-> (load+ (:pathname rinf))
           (.then (fn [result]
                    (update-v result))))
       js/undefined)
     #js[])
    (if v
      [menu-ui ctx v]
      [:div])))

(defn main-ui []
  ;; FIXME: prefix with /app
  (let [[rinf set-rinf] (react/useState (get-rinf))
        ctx {:rinf rinf :set-rinf set-rinf}
        new-url (rinf->url rinf)
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

    (case (-> #pp rinf :route :name)
      :root
      [select-ui ctx]
      :example
      [load-ui ctx]
      nil
      [:div "Route not found"])))

(defn init []
  (when-let [[_ kind] (re-matches #"^/from-hash/(json|edn)$" js/location.pathname)]
    (let [params (js/URLSearchParams. (-> js/location.hash
                                          (str/replace #"^#" "")))
          data (.get params "data")
          id (random-uuid)]
      (js/localStorage.setItem (str id) data)
      (js/history.pushState {} nil (str js/location.origin (str "/blob/" kind "/" id))))))
