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
      [:title title]
      [:style "body{max-width:960px;margin:2rem auto;padding:0 1rem;font-family:system-ui,sans-serif;line-height:1.45} section{margin:2rem 0} table{border-collapse:collapse;width:100%} th,td{border:1px solid #ccc;padding:.45rem;text-align:left} th{background:#f3f3f3} fieldset{margin:1rem 0;padding:1rem} label{display:block;margin:.45rem 0} input,select,button{font:inherit;padding:.25rem} .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:.5rem} .notice{padding:.75rem;background:#e6f4ea} .errors{padding:.75rem;background:#fdecea} .numeric{text-align:right}"]]
     [:body content]])))

(comment
  (page "Pommes Connection" [:h1 "Hello"])
  )
