(ns dev.lhrb.pommes.pommes-connection.views.actions
  (:require [clojure.string :as str]
            [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
            [dev.lhrb.pommes.pommes-connection.game-rules :as game-rules]
            [dev.lhrb.pommes.pommes-connection.money :as money]
            [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
            [ring.util.response :as response]))

(defn- money [value] (money/format-euros value))
(def action-icons {:send-punks "action_punk" :rocker-meeting "action_rocker" :influencer-visit "action_influenca"
                   :parking-chaos "action_park_chaos" :review-bomb "action_review_bomb" :neighborhood-festival "action_festival"})
(def action-stories
  {:send-punks "Die Punks kommen mit einem Kassettenrekorder, dessen Batterien seit 2004 als politisch gelten. Nach zwei Liedern diskutiert der Hausmeister mit ihnen über Zuständigkeiten."
   :rocker-meeting "Vor der Tür stehen Motorräder in einer Ordnung, die nur der Vorsitzende versteht. Drinnen wird über Lautstärke gesprochen, als sei sie eine Genehmigung."
   :influencer-visit "Ein Mensch mit Ringlicht bestellt etwas Kleines und nennt es einen Moment. Bald stehen Leute davor und warten darauf, dass der Moment wieder aufgeht."
   :parking-chaos "Jemand hat die Parkplätze neu ausgelegt, ohne die Autos zu informieren. Ein Kombi steht quer und sein Besitzer vertritt inzwischen eine Haltung."
   :review-bomb "Plötzlich weiß das Internet sehr genau, was hier angeblich passiert. Die meisten Verfasser waren noch nie da, aber einer kennt die Speisekarte von früher."
   :neighborhood-festival "Der Nachbarschaftsrat hat Bierzeltgarnituren bestellt und keine Rückfrage zugelassen. Gegen Abend sind die Pommesgabeln knapp, doch die Stimmung gilt als geregelt."})
(defn- targets [action viewer players] (case (:target action) :other-player (remove #(= (:id %) (:id viewer)) players) :any-player players []))

(defn- slot-detail [{:keys [action target-player-name state apply-at-tick effect-ends-at-tick available-at-tick]}]
  [:li.action-slot
   [:div.action-slot__title
    [:strong (:name action)]
    [:span " → " target-player-name]]

   [:small
    (case state
      :scheduled (str "Startet mit Tick " apply-at-tick)
      :active (str "Wirkt bis Tick " effect-ends-at-tick)
      "Wirkung beendet")
    (str "; Slot frei ab Tick " available-at-tick)
    ]])

(defn- slots-panel [actions slots]
  (let [{:keys [maximum-uses uses-left]} (first actions)
        empty-slots (- maximum-uses (count slots))]
    (layout/panel
     "action-slots"
     [[:div.offer-card__top
       [:div [:h2 "Maßnahmenfenster"]
        [:p.meta (format "%d von %d Maßnahmen frei" uses-left maximum-uses)]]
       (layout/badge (str uses-left " frei"))]
      [:ol.action-slot-list
       (map slot-detail slots)
       (for [_ (range empty-slots)]
         [:li.action-slot.action-slot--empty [:strong "Frei"] [:small "Bereit für eine Maßnahme."]])]])))

(defn action-card [game viewer players token action]
  (let [options (targets action viewer players) id (name (:id action))]
    [:article {:id (str "action-" id) :class "offer-card action-card"}
     [:div.offer-card__top (layout/icon (get action-icons (:id action) "nav_action") "") [:div [:h2 (:name action)] [:p.meta (get action-stories (:id action))]]]
     [:p.meta (format "Wirkt %.1f Std."
                      (/ (:duration action) (double (game-rules/ticks-per-hour game-data/game-data))))]
     [:p [:strong (money (:cost action))] " · " (if (:available? action) "bereit" "das Maß ist vorerst voll")]
     [:form {:method "post" :action (str "/games/" (:game-token game) "/actions")
             :hx-post (str "/games/" (:game-token game) "/actions") :hx-target "#actions-content" :hx-swap "outerHTML"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value token}] [:input {:type "hidden" :name "action-id" :value id}]
      [:label {:for (str "target-" id)} "Ziel"
       [:select {:id (str "target-" id) :name "target-player-id"} (for [target options] [:option {:value (:id target)} (:name target)])]]
      (layout/button {:disabled (or (not (:available? action)) (empty? options))} (if (:available? action) "Los schicken" "Noch nicht bereit"))]]))

(defn content [{:keys [game viewer players actions slots anti-forgery-token]}]
  [:section {:id "actions-content"
             :hx-get (str "/games/" (:game-token game) "/actions/content")
             :hx-trigger "pommes:tick"
             :hx-target "this"
             :hx-swap "outerHTML"}
   (slots-panel actions slots)
   (map #(action-card game viewer players anti-forgery-token %) actions)])

(defn page [{:keys [game viewer players actions slots anti-forgery-token notice hx?] :as data}]
  (-> (layout/game-response (str (:name game) " | Aktionen") game viewer :actions
                            [[:header.screen-head (layout/icon "nav_action" "") [:div [:h1 "Aktionen"] [:p "Kleine Maßnahmen mit großen Folgen."]]]
                             (when notice (layout/notice (first notice) (second notice)))
                             (content data)] hx?)
      response/response (response/content-type "text/html; charset=utf-8")))

(defn mutation [{:keys [viewer kind message] :as data}]
  (layout/mutation-response (content data) viewer kind message))

(comment
  (get action-stories :send-punks))
