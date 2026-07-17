(ns dev.lhrb.pommes.pommes-connection.views.layout
  (:require
   [hiccup2.core :as h]))

(defn panel
  ([content] (panel nil content))
  ([class content]
   (into [:section {:class (str "panel" (when class (str " " class)))}] content)))

(defn button [label]
  [:button {:class "button" :type "submit"} label])

(defn badge [label]
  [:span {:class "badge"} label])

(defn notice [kind content]
  [:div {:class (str "notice notice--" kind) :role (if (= kind "error") "alert" "status")}
   content])

(defn offer-card [content]
  (into [:article.offer-card] content))

(defn modal [content]
  (into [:div {:class "modal" :role "alert"}] content))

(defn stat-row [label value]
  [:div.stat-row [:span label] [:strong value]])

(defn meter [value]
  [:div.meter {:aria-hidden "true"}
   [:span {:style (str "width:" (max 8 (min 100 (long value))) "%")}]] )

(defn page
  [title content]
  (str
   (h/html
    [:html {:lang "de"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css2?family=Press+Start+2P&display=swap"}]
      [:link {:rel "stylesheet" :href "/assets/css/app.css"}]
      [:title title]]
     [:body content]])))

(comment
  (page "Pommes Connection" [:main [:h1 "Hello"]]))
