(ns dev.lhrb.pommes.pommes-connection.views.black-market
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.black-market :as black-market]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- percent [value]
  (format "%.1f%%" (* 100.0 (double (or value 0.0)))))

(defn- item-card [game token item]
  (layout/panel
   "panel--yellow"
   [[:h2 (:name item)]
    [:p (:description item)]
    [:p.meta (str "Stufe " (:level item) " / " (:maximum-level item)
                   " · Verdeckung " (percent (* (black-market/progress (:level item))
                                                   (:maximum-concealment item))))]
    [:form {:method "post" :action (str "/games/" (:game-token game) "/black-market")}
     [:input {:type "hidden" :name "__anti-forgery-token" :value token}]
     [:input {:type "hidden" :name "item-id" :value (name (:id item))}]
     [:button {:type "submit" :disabled (not (:available? item))}
      (cond
        (>= (:level item) (:maximum-level item)) "Ausgereizt"
        (:available? item) (str "Ausbauen · " (money (:cost item)))
        :else (str "Benötigt " (money (:cost item))))]]]))

(defn page [{:keys [game viewer items anti-forgery-token notice]}]
  (let [config (:black-market game-data/game-data)
        stats (or (:game-stats viewer) {})
        configured-concealment (black-market/concealment-rate config (:black-market viewer))
        suspicion (double (or (:suspicion stats) 0.0))
        penalty-ticks (long (or (:audit-penalty-ticks stats) 0))
        concealment (if (pos? penalty-ticks) 0.0 configured-concealment)
        body (layout/page
              (str (:name game) " | Schwarzmarkt")
              [:main
               [:header.masthead
                [:p.eyebrow "Pommes Connection · Hinterzimmer"]
                [:h1 "Schwarzmarkt"]
                [:p.subhead (:name viewer) " · Kasse: " (money (:money viewer))]
                [:p.meta [:a {:href (str "/games/" (:game-token game))} "← Zur Budenübersicht"]]]
               (when notice (layout/notice (first notice) (second notice)))
               (when (pos? penalty-ticks)
                 (layout/notice "error" (str "Betriebsprüfung! Verdeckung ist noch " penalty-ticks
                                             " Ticks gesperrt, der Steuersatz ist verdoppelt.")))
               (layout/panel "panel--tight"
                             [[:h2 "Steuerlage"]
                              [:div.grid.grid--two
                               (layout/stat-row "Steuersatz" (percent (:tax-rate config)))
                               (layout/stat-row "Verdeckung" (percent concealment))
                               (layout/stat-row "Verdacht" (percent suspicion))
                               (layout/stat-row "Steuern bezahlt" (money (:total-tax-paid stats)))
                               (layout/stat-row "Steuern vermieden" (money (:total-tax-avoided stats)))
                               (layout/stat-row "Prüfungssperre" (str penalty-ticks " Ticks"))]
                              (layout/meter (* 100.0 (min 1.0 suspicion)))])
               (map #(item-card game anti-forgery-token %) items)])]
    (response/content-type (response/response body) "text/html; charset=utf-8")))

(comment
  ;; (page {:game {:name "Lunch" :game-token "ABC123"} :viewer {:name "Ada" :money 20}})
  )
