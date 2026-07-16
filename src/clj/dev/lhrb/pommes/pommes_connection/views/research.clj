(ns dev.lhrb.pommes.pommes-connection.views.research
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.regulars-simulation :as regulars]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- label [value]
  ({:students "Studierende" :families "Familien" :retirees "Rentnerinnen und Rentner"
    :office-workers "Büromenschen" :yuppies "Yuppies" :foodies "Feinschmeckerinnen und Feinschmecker"}
   value (-> value name (str/replace #"-" " ") str/capitalize)))

(defn- percent [item]
  (format "%.0f%%" (* 100.0 (:maximum-modifier item)
                     (regulars/research-progress (:level item)))))

(defn- leaders [rows]
  (if (seq rows)
    (str/join ", " (map :player-name rows))
    "Noch keine Forschung"))

(defn- research-card [game token item]
  (layout/panel "panel--yellow"
                [[:h2 (:name item)]
                 [:p (:description item)]
                 [:p.meta (str "Stufe " (:level item) " / " (:maximum-level item)
                                " · Wirkung " (percent item))]
                 [:p.meta (str "Stadtspitze: " (leaders (:leaders item)))]
                 [:form {:method "post" :action (str "/games/" (:game-token game) "/research")}
                  [:input {:type "hidden" :name "__anti-forgery-token" :value token}]
                  [:input {:type "hidden" :name "research-id" :value (name (:id item))}]
                  [:button {:type "submit" :disabled (not (:available? item))}
                   (cond
                     (>= (:level item) (:maximum-level item)) "Ausgeforscht"
                     (:available? item) (str "Verbessern · " (money (:cost item)))
                     :else (str "Benötigt " (money (:cost item))))]]]))

(defn page [{:keys [game viewer research anti-forgery-token notice]}]
  (let [body (layout/page
              (str (:name game) " | Forschung")
              [:main
               [:header.masthead
                [:p.eyebrow "Pommes Connection · Versuchsküche"]
                [:h1 "Forschung"]
                [:p.subhead (:name viewer) " · Kasse: " (money (:money viewer))]
                [:p.meta [:a {:href (str "/games/" (:game-token game))} "← Zur Budenübersicht"]]]
               (when notice (layout/notice (first notice) (second notice)))
               (layout/panel "panel--tight"
                             [[:h2 "Stammgäste"]
                              [:div.grid.grid--two
                               (for [[demographic count] (:regulars viewer)]
                                 (layout/stat-row (label demographic) count))]])
               (map #(research-card game anti-forgery-token %) research)])]
    (response/content-type (response/response body) "text/html; charset=utf-8")))

(comment
  ;; (page {:game {:name "Lunch" :game-token "ABC123"} :viewer {:name "Ada" :money 20}})
  )
