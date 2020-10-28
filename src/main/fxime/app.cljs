(ns fxime.app
  (:require [fxime.core :as core]
            [reagent.core :as r]
            [reagent.dom :as rd]))

(def functional-compiler (r/create-compiler {:function-components true}))

(defn ^:dev/after-load init []
  (core/init)
  (rd/render [core/router-ui]
             (js/document.getElementById "app")
             functional-compiler))
