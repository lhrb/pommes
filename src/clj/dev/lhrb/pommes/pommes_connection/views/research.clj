(ns dev.lhrb.pommes.pommes-connection.views.research
  (:require [clojure.string :as str]
            [dev.lhrb.pommes.pommes-connection.regulars-simulation :as regulars]
            [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
            [ring.util.response :as response]))
(defn- money [v] (str "€" (str/replace (format "%.2f" (double (or v 0))) "." ",")))
(def icons {:potato-quality "research_potato" :fryer "research_fryer" :fry-grease "research_grease" :ketchup-recipe "reseach_ketchup" :mayo-recipe "reseach_majo"})
(def demographic-icons {:students "dem_student" :families "dem_families" :retirees "dem_retiree" :office-workers "dem_worker" :yuppies "dem_yuppi" :foodies "dem_foodie"})
(defn- label [v] ({:students "Studierende" :families "Familien" :retirees "Rentner" :office-workers "Büromenschen" :yuppies "Yuppies" :foodies "Feinschmecker"} v (name v)))
(defn- regular-row [demographic count]
  [:div.regular-row
   [:span.regular-row__label
    [:img {:src (str "/assets/icons/" (get demographic-icons demographic "dem_foodie") "_48.png")
           :alt "" :width 48 :height 48}]
    (label demographic)]
   [:strong (layout/number count)]])
(defn research-card [game token item]
  [:article {:id (str "research-" (name (:id item))) :class "offer-card"}
   [:div.offer-card__top [:div [:h2 (:name item)] [:p (:description item)]] (layout/icon (get icons (:id item) "nav_research") "")]
   [:p.meta (format "Stufe %d / %d · Wirkung %.0f%%" (:level item) (:maximum-level item) (* 100 (:maximum-modifier item) (regulars/research-progress (:level item))))]
   [:p.meta "Stadtspitze: " (if (seq (:leaders item)) (str/join ", " (map :player-name (:leaders item))) "noch niemand")]
   [:form {:method "post" :action (str "/games/" (:game-token game) "/research") :hx-post (str "/games/" (:game-token game) "/research") :hx-target (str "#research-" (name (:id item))) :hx-swap "outerHTML"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value token}] [:input {:type "hidden" :name "research-id" :value (name (:id item))}]
    (layout/button
     {:disabled (not (:available? item))}
     (cond
       (>= (:level item) (:maximum-level item)) "Ausgeforscht"
       (:available? item) ["Forschen · " (layout/number (money (:cost item)))]
       :else ["Benötigt " (layout/number (money (:cost item)))]))]])
(defn page [{:keys [game viewer research anti-forgery-token notice hx?]}]
  (-> (layout/game-response (str (:name game) " | Forschung") game viewer :research
                            [[:header.screen-head (layout/icon "nav_research" "") [:div [:h1 "Forschung"] [:p "Die Fritteuse lernt langsam, aber bestimmt."]]]
                             (when notice (layout/notice (first notice) (second notice)))
                             (layout/panel "panel--quiet" [[:h2 "Stammgäste"] [:div.grid.grid--two (for [[d c] (:regulars viewer)] (regular-row d c))]])
                             (map #(research-card game anti-forgery-token %) research)] hx?) response/response (response/content-type "text/html; charset=utf-8")))
(defn mutation [{:keys [game viewer research anti-forgery-token research-id kind message]}]
  (layout/mutation-response (research-card game anti-forgery-token (some #(when (= (:id %) research-id) %) research)) viewer kind message))
