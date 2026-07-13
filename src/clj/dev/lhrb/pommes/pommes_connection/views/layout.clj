(ns dev.lhrb.pommes.pommes-connection.views.layout
  (:require
   [hiccup2.core :as h]))

(defn page
  [title content]
  (str
   (h/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title title]]
     [:body content]])))

(comment
  (page "Pommes Connection" [:h1 "Hello"])
  )
