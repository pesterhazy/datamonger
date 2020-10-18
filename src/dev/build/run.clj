(ns build.run
  (:require
   [shadow.cljs.devtools.api :as shadow]
   ;; for side-effects
   [hashpr.hashpr]))

(defn run
  {:shadow/requires-server true}
  [& args]
  (shadow/watch :app)
  (shadow/watch :test))
