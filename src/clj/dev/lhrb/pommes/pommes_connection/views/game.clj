(ns dev.lhrb.pommes.pommes-connection.views.game
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- quantity [value]
  (format "%d" (long (Math/round (double (or value 0.0))))))

(defn- viewer-tick-summary [latest-tick viewer-id]
  (some #(when (= viewer-id (:player-id %)) %) (:players latest-tick)))

(defn- city-notice [game viewer-result scheduled-for]
  (cond
    scheduled-for ["good" (str "Menüänderungen sind für Tick " scheduled-for " notiert. Die Nachbarschaft spitzt die Ohren.")]
    (nil? viewer-result) ["quiet" "Verdächtig ruhig. Der erste Mittagsmob ist noch nicht eingerollt."]
    (pos? (:profit viewer-result)) ["good" "Eilmeldung vom Mittagstisch: Deine Bude hat Gewinn gemacht."]
    :else ["warning" "Merkwürdige Kritik im Lokalblatt: Beim nächsten Ansturm lieber auf die Marge schielen."]))

(defn- player-card [player viewer]
  (layout/offer-card
   [[:div.offer-card__top
     [:strong (:name player)]
     (when (= (:id player) (:id viewer)) (layout/badge "Du"))]
    [:div.stat-row [:span "Buden-Kasse"] [:strong (money (:money player))]]
    [:div.stat-row [:span "Prestige"] [:strong (quantity (:prestige player))]]]))

(defn- chronicle-panel [game events]
  (layout/panel
   "panel--tight"
   [[:div.offer-card__top
     [:h2 "Stadtchronik"]
     (layout/badge "Öffentlich")]
    (if (seq events)
      [:ol {:style "margin:0;padding-left:20px"}
       (for [{:keys [tick message]} events]
         [:li {:style "margin:0 0 10px"}
          [:strong (str "Tick " tick ": ")]
          message])]
      [:p.meta "Noch schreibt die Stadt an ihrer ersten Schlagzeile."])
    [:a.button {:href (str "/games/" (:game-token game) "/chronicle")}
     "Ganze Chronik lesen"]]))

(defn page
  [{:keys [game viewer players latest-tick scheduled-for events hx? server-now]}]
  (let [viewer-result (viewer-tick-summary latest-tick (:id viewer))]
    (-> (layout/game-response
         (str (:name game) " | Pommes Connection") game viewer :game
         [[:header.screen-head
           (layout/icon "nav_game" "")
           [:div [:h1 "Deine Bude"] [:p (:name viewer) " · Tick " (:tick game)]]]
          (let [[kind message] (city-notice game viewer-result scheduled-for)]
            (layout/notice kind message))
          (layout/panel "panel--yellow"
                        [[:div.offer-card__top
                          [:h2 "Budenbericht"]
                          (layout/badge "Heute")]
                         (if viewer-result
                           [:div.grid.grid--three
                            (layout/stat-row "Verkäufe" (quantity (:sales viewer-result)))
                            (layout/stat-row "Gewinn" (money (:profit viewer-result)))
                            (layout/stat-row "Kasse" (money (:money viewer-result)))]
                           [:p "Noch keine Einnahmen. Bau ein Menü, bevor der erste Mob auftaucht."])])
          (chronicle-panel game events)
          (when viewer-result
            (layout/panel "panel--tight"
                          [[:h2 (str "Quittung für Tick " (:tick latest-tick))]
                           [:div.grid.grid--two
                            (layout/stat-row "Umsatz" (money (:revenue viewer-result)))
                            (layout/stat-row "Prestige" (quantity (:prestige viewer-result)))]
                           ]))
          (layout/panel "panel--quiet"
                        [[:h2 "Pinnwand"]
                         [:p.invite "Einladungslink · weitere Fritteuse gesucht: "
                          [:a {:href (str "/join/" (:invite-token game))}
                           (str "/join/" (:invite-token game))]]])
          ] hx? server-now)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(defn chronicle-page
  [{:keys [game viewer events page page-count hx?]}]
  (let [navigation
        (when (> page-count 1)
          [:nav {:aria-label "Chronikseiten" :style "display:flex;gap:12px;margin-top:18px"}
           (when (pos? page)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (dec page))}
              "Neuere Meldungen"])
           (when (< (inc page) page-count)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (inc page))}
              "Ältere Meldungen"])])]
    (-> (layout/game-response
         (str (:name game) " | Stadtchronik") game viewer :chronicle
         [[:header.screen-head (layout/icon "nav_chronic" "") [:div [:h1 "Stadtchronik"] [:p "Was die Stadt nie vergessen wird."]]]
          (layout/panel
           "panel--yellow"
           [[:h2 "Die Schlagzeilen"]
            (if (seq events)
              [:ol {:style "margin:0;padding-left:20px"}
               (for [{:keys [tick message]} events]
                 [:li {:style "margin:0 0 14px"}
                  [:strong (str "Tick " tick " · ")]
                  message])]
              [:p "Noch keine Ereignisse. Die Stadt sammelt Material."])
            navigation])] hx?)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (page {:game {:id 1 :name "Lunch rush" :tick 0} :viewer {:id 1 :name "Ada"}})
  ;; (chronicle-page {:game {:name "Lunch" :game-token "ABC123"} :events [] :page 0 :page-count 0})
  )
