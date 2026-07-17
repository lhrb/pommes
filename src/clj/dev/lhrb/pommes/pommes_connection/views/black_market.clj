(ns dev.lhrb.pommes.pommes-connection.views.black-market
  (:require [clojure.string :as str] [dev.lhrb.pommes.pommes-connection.black-market :as black-market]
            [dev.lhrb.pommes.pommes-connection.game-data :as game-data] [dev.lhrb.pommes.pommes-connection.views.layout :as layout] [ring.util.response :as response]))
(defn- money [v] (str "€" (str/replace (format "%.2f" (double (or v 0))) "." ",")))
(defn- percent [v] (format "%.1f%%" (* 100.0 (double (or v 0)))))
(def icons {:creative-bookkeeping "bm_bookkeeping" :cash-only-register "bm_cash" :uncle-rudis-accounting "bm_uncle"})
(defn item-card [game token item]
  [:article {:id (str "black-market-" (name (:id item))) :class "offer-card"}
   [:div.offer-card__top (layout/icon (get icons (:id item) "nav_black") "") [:div [:h2 (:name item)] [:p (:description item)]]]
   [:p.meta (str "Stufe " (:level item) " / " (:maximum-level item) " · Verdeckung " (percent (* (black-market/progress (:level item)) (:maximum-concealment item))))]
   [:form {:method "post" :action (str "/games/" (:game-token game) "/black-market") :hx-post (str "/games/" (:game-token game) "/black-market") :hx-target (str "#black-market-" (name (:id item))) :hx-swap "outerHTML"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value token}] [:input {:type "hidden" :name "item-id" :value (name (:id item))}]
    (layout/button {:disabled (not (:available? item))} (cond (>= (:level item) (:maximum-level item)) "Ausgereizt" (:available? item) (str "Ausbauen · " (money (:cost item))) :else (str "Benötigt " (money (:cost item)))))]])
(defn page [{:keys [game viewer items anti-forgery-token notice hx?]}]
  (let [config (:black-market game-data/game-data) stats (or (:game-stats viewer) {}) penalty (long (or (:audit-penalty-ticks stats) 0)) concealment (if (pos? penalty) 0 (black-market/concealment-rate config (:black-market viewer)))]
    (-> (layout/game-response (str (:name game) " | Schwarzmarkt") game viewer :black-market
                              [[:header.screen-head (layout/icon "nav_black" "") [:div [:h1 "Schwarzmarkt"] [:p "Kein Geschäft ist zu klein für eine Nebenrechnung."]]]
                               (when notice (layout/notice (first notice) (second notice)))
                               (layout/panel "panel--quiet" [[:h2 "Steuerlage"] [:div.grid.grid--two (layout/stat-row "Steuersatz" (percent (:tax-rate config))) (layout/stat-row "Verdeckung" (percent concealment)) (layout/stat-row "Verdacht" (percent (:suspicion stats))) (layout/stat-row "Prüfung" (str penalty " Ticks"))]])
                               (map #(item-card game anti-forgery-token %) items)] hx?) response/response (response/content-type "text/html; charset=utf-8"))))
(defn mutation [{:keys [game viewer items anti-forgery-token item-id kind message]}]
  (layout/mutation-response (item-card game anti-forgery-token (some #(when (= (:id %) item-id) %) items)) viewer kind message))
