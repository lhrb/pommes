(ns dev.lhrb.pommes.pommes-connection.views.actions
  (:require [clojure.string :as str]
            [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
            [ring.util.response :as response]))

(defn- money [value] (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))
(def action-icons {:send-punks "action_punk" :rocker-meeting "action_rocker" :influencer-visit "action_influenca"
                   :parking-chaos "action_park_chaos" :review-bomb "action_review_bomb" :neighborhood-festival "action_festival"})
(defn- label [value] ({:students "Studierende" :families "Familien" :retirees "Rentner" :office-workers "Büromenschen" :yuppies "Yuppies" :foodies "Feinschmecker"} value (name value)))
(defn- effect-label [{:keys [type demographic modifier]}]
  (case type :demographic-attraction (str (if (neg? modifier) "Weniger " "Mehr ") (label demographic))
        :demographic-repulsion (str "Weniger " (label demographic))
        :global-attraction (str (format "%+.0f%%" (* 100 modifier)) " Anziehung")
        :average-order-size (str (format "%+.0f%%" (* 100 modifier)) " Bestellgröße")
        :price-tolerance (str (format "%+.0f%%" (* 100 modifier)) " Preistoleranz") (name type)))
(defn- targets [action viewer players] (case (:target action) :other-player (remove #(= (:id %) (:id viewer)) players) :any-player players []))

(defn action-card [game viewer players token action]
  (let [options (targets action viewer players) id (name (:id action))]
    [:article {:id (str "action-" id) :class "offer-card action-card"}
     [:div.offer-card__top (layout/icon (get action-icons (:id action) "nav_action") "") [:div [:h2 (:name action)] [:p.meta (str/join " · " (map effect-label (:effects action)))]]]
     [:p.meta (str "Wirkt " (* 30 (:duration action)) " Sek. · Pause " (* 30 (:cooldown action)) " Sek.")]
     [:p [:strong (money (:cost action))] " · " (if (:available? action) "bereit" (str "bereit ab Tick " (:available-at-tick action)))]
     [:form {:method "post" :action (str "/games/" (:game-token game) "/actions")
             :hx-post (str "/games/" (:game-token game) "/actions") :hx-target (str "#action-" id) :hx-swap "outerHTML"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value token}] [:input {:type "hidden" :name "action-id" :value id}]
      [:label {:for (str "target-" id)} "Ziel"
       [:select {:id (str "target-" id) :name "target-player-id"} (for [target options] [:option {:value (:id target)} (:name target)])]]
      (layout/button {:disabled (or (not (:available? action)) (empty? options))} (if (:available? action) "Los schicken" "Noch nicht bereit"))]]))

(defn page [{:keys [game viewer players actions anti-forgery-token notice hx?]}]
  (-> (layout/game-response (str (:name game) " | Aktionen") game viewer :actions
                            [[:header.screen-head (layout/icon "nav_action" "") [:div [:h1 "Aktionen"] [:p "Kleine Maßnahmen mit großen Folgen."]]]
                             (when notice (layout/notice (first notice) (second notice)))
                             (map #(action-card game viewer players anti-forgery-token %) actions)] hx?)
      response/response (response/content-type "text/html; charset=utf-8")))

(defn mutation [{:keys [game viewer players actions anti-forgery-token action-id kind message]}]
  (layout/mutation-response (action-card game viewer players anti-forgery-token (some #(when (= (:id %) action-id) %) actions)) viewer kind message))
