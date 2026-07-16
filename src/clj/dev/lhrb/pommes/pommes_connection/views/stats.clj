(ns dev.lhrb.pommes.pommes-connection.views.stats
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- quantity [value]
  (format "%d" (long (Math/round (double (or value 0.0))))))

(defn- percent [value]
  (format "%.1f%%" (* 100.0 (double (or value 0.0)))))

(defn- label [value]
  ({:students "Studierende"
    :families "Familien"
    :retirees "Rentnerinnen und Rentner"
    :office-workers "Büromenschen"
    :yuppies "Yuppies"
    :foodies "Feinschmeckerinnen und Feinschmecker"}
   value
   (-> value name (str/replace #"-" " ") str/capitalize)))

(defn- total-sales [player]
  (reduce + 0 (vals (get-in player [:game-stats :demographic-sales] {}))))

(defn- total-regulars [player]
  (reduce + 0 (vals (:regulars player))))

(defn- active-effects [player]
  (if-let [effects (seq (:effects player))]
    (str/join ", "
              (map (fn [effect]
                     (let [action (game-data/action (:action-id effect))]
                       (str (or (:name action) "Unruhe")
                            " (" (:ticks-left effect) " Ticks)")))
                   effects))
    "–"))

(defn- city-panel [city]
  (let [population (long (or (:population city) 0))]
    (layout/panel
     "panel--yellow"
     [[:h2 "Die Stadt heute"]
      [:p.meta (str (quantity population) " Menschen, die auf einen Pommes-Moment warten.")]
      [:table
       [:thead [:tr [:th "Gruppe"] [:th.numeric "Einwohner"] [:th.numeric "Stadtanteil"]
                [:th.numeric "Bestellungen / Tick"]]]
       [:tbody
        (for [{:keys [id population eat-rate]} (:demographics city)]
          [:tr
           [:td (label id)]
           [:td.numeric (quantity population)]
           [:td.numeric (percent (if (pos? (long (or (:population city) 0)))
                                   (/ (double population) (:population city))
                                   0.0))]
           [:td.numeric (quantity (* (double population) (double eat-rate)))]] )]]])))

(defn- market-share-panel [city players]
  (layout/panel
   "panel--tight"
   (into [[:h2 "Marktanteile · ganze Spielzeit"]
          [:p.meta "Verkaufte Portionen nach Kundengruppe. Noch ohne Verkäufe bleibt der Anteil offen."]]
         (for [{:keys [id]} (:demographics city)
               :let [sales (fn [player] (long (get-in player [:game-stats :demographic-sales id] 0)))
                     total (reduce + 0 (map sales players))]]
           [:section
            [:h3 (label id)]
            [:table
             [:thead [:tr [:th "Bude"] [:th.numeric "Portionen"] [:th.numeric "Marktanteil"]]]
             [:tbody
              (for [player (sort-by (juxt (comp - sales) :name) players)]
                [:tr
                 [:td (:name player)]
                 [:td.numeric (quantity (sales player))]
                 [:td.numeric (if (pos? total)
                                (percent (/ (double (sales player)) total))
                                "Noch offen")]])]]]))))

(defn- leaderboard-panel [players]
  (layout/panel
   "panel--yellow"
   [[:h2 "Budenchronik"]
    [:table
     [:thead [:tr [:th "Bude"] [:th.numeric "Kasse"] [:th.numeric "Prestige"]
              [:th.numeric "Portionen"] [:th.numeric "Stammgäste"]]]
     [:tbody
      (for [player (sort-by (juxt (comp - :prestige) :name) players)]
        [:tr
         [:td (:name player)]
         [:td.numeric (money (:money player))]
         [:td.numeric (quantity (:prestige player))]
         [:td.numeric (quantity (total-sales player))]
         [:td.numeric (quantity (total-regulars player))]])]]]))

(defn- research-panel [players]
  (layout/panel
   "panel--tight"
   [[:h2 "Forschungsrennen"]
    [:table
     [:thead [:tr [:th "Projekt"] [:th.numeric "Beste Stufe"] [:th "Stadtspitze"]]]
     [:tbody
      (for [[research-id item] (game-data/research-items)
            :let [level (apply max 0 (map #(long (get-in % [:research research-id] 0)) players))
                  leaders (->> players
                               (filter #(= level (long (get-in % [:research research-id] 0))))
                               (map :name)
                               (str/join ", "))]]
        [:tr
         [:td (:name item)]
         [:td.numeric (quantity level)]
         [:td (if (pos? level) leaders "Noch keine Forschung")]])]]]))

(defn- underworld-panel [players]
  (layout/panel
   "panel--quiet"
   [[:h2 "Steuern & Hinterzimmer"]
    [:table
     [:thead [:tr [:th "Bude"] [:th.numeric "Steuern bezahlt"] [:th.numeric "Steuern vermieden"]
              [:th.numeric "Verdacht"] [:th "Prüfung / Unruhe"]]]
     [:tbody
      (for [player (sort-by :name players)
            :let [stats (:game-stats player)
                  penalty-ticks (long (or (:audit-penalty-ticks stats) 0))]]
        [:tr
         [:td (:name player)]
         [:td.numeric (money (:total-tax-paid stats))]
         [:td.numeric (money (:total-tax-avoided stats))]
         [:td.numeric (percent (:suspicion stats))]
         [:td (str (if (pos? penalty-ticks)
                     (str "Prüfung: " penalty-ticks " Ticks")
                     "Keine Prüfung")
                   " · " (active-effects player))]])]]]))

(defn page [{:keys [game viewer players city]}]
  (let [body (layout/page
              (str (:name game) " | Stadtstatistik")
              [:main
               [:header.masthead
                [:p.eyebrow "Pommes Connection · Lokalausgabe"]
                [:h1 "Stadtstatistik"]
                [:p.subhead (:name game) " · Tick " (:tick game)]
                [:p.meta [:a {:href (str "/games/" (:game-token game))} "← Zur Budenübersicht"]]]
               (layout/notice "quiet"
                              (str "Öffentliche Zahlen für " (:name viewer)
                                   " und alle anderen Buden."))
               (city-panel city)
               (market-share-panel city players)
               (leaderboard-panel players)
               (research-panel players)
               (underworld-panel players)])]
    (response/content-type (response/response body) "text/html; charset=utf-8")))

(comment
  ;; (page {:game {:name "Lunch" :game-token "ABC123" :tick 4}
  ;;        :viewer {:name "Ada"} :players [] :city {:population 0 :demographics []}})
  )
