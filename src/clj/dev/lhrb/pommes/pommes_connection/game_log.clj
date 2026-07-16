(ns dev.lhrb.pommes.pommes-connection.game-log
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]))

(def ^:private regular-milestones [1 10 50 100 500 1000 5000 10000 50000 100000])
(def ^:private city-milestones [1000 10000 100000 500000 900000])
(def ^:private daily-ticks 2880)

(defn- label [id]
  ({:students "Studierende"
    :families "Familien"
    :retirees "Rentnerinnen und Rentner"
    :office-workers "Büromenschen"
    :yuppies "Yuppies"
    :foodies "Feinschmeckerinnen und Feinschmecker"}
   id
   (-> id name (str/replace #"-" " ") str/capitalize)))

(defn- player-by-id [state]
  (into {} (map (juxt :id identity)) (:players state)))

(defn- total-regulars [player]
  (reduce + 0 (vals (:regulars player))))

(defn- crossed-milestone [before after milestones]
  (last (filter #(and (< before %) (<= % after)) milestones)))

(defn- action-events [state actions]
  (let [players (player-by-id state)]
    (keep (fn [{:keys [game-player-id kind payload]}]
            (when (str/starts-with? (name kind) "action-")
              (let [action (game-data/action (:action-id payload))
                    actor (get players game-player-id)
                    target (get players (:target-game-player-id payload))]
                {:kind :action
                 :message (case (:id action)
                            :send-punks (str (:name actor) " hat Punks zu " (:name target)
                                             " geschickt. Die Nachbarschaft sucht ihre Ohrstöpsel.")
                            :rocker-meeting (str "Vor " (:name target) " parken jetzt Motorräder: "
                                                (:name actor) " organisiert ein Rockertreffen.")
                            :influencer-visit (str (:name actor) " hat einen Influencer bei " (:name target)
                                                  " abgesetzt. Ringlicht schlägt Mittagspause.")
                            :parking-chaos (str (:name actor) " hat vor " (:name target)
                                                " Parkplatzchaos bestellt. Niemand weiß, wo der Kombi steht.")
                            :review-bomb (str (:name actor) " bombardiert " (:name target)
                                              " mit Bewertungen. Sogar die Fritteuse hat jetzt Sterne.")
                            :neighborhood-festival (str (:name actor) " veranstaltet bei " (:name target)
                                                        " ein Nachbarschaftsfest. Die Pommesgabeln sind offiziell knapp.")
                            (str (:name actor) " löst bei " (:name target) " Unruhe aus."))})))
          actions)))

(defn- audit-events [before after]
  (keep (fn [player]
          (when (get-in player [:last-tick :audited?])
            {:kind :audit
             :message (str "Betriebsprüfung Nummer " (get-in player [:game-stats :audit-count])
                           " bei " (:name player) ". Das Finanzamt fand die Kasse "
                           "interessanter als die Speisekarte.")}))
        (:players after)))

(defn- regular-events [before after]
  (keep (fn [player]
          (let [previous (get (player-by-id before) (:id player))
                before-count (total-regulars previous)
                after-count (total-regulars player)]
            (when-let [milestone (crossed-milestone before-count after-count regular-milestones)]
              (let [[demographic _]
                    (apply max-key second
                           (for [[id count] (:regulars player)]
                             [id (- count (get-in previous [:regulars id] 0))]))]
                {:kind :regulars
                 :message (str (:name player) " hat " milestone " Stammgäste erreicht. "
                               (label demographic) " tragen die Nachricht weiter als jede Litfaßsäule.")}))))
        (:players after)))

(defn- city-events [before after]
  (when-let [milestone (crossed-milestone (get-in before [:city :population] 0)
                                          (get-in after [:city :population] 0)
                                          city-milestones)]
    [{:kind :city-growth
      :message (str "Die Stadt knackt " milestone " Einwohner. Der Pommesbedarf hat jetzt eine eigene Postleitzahl.")}]))

(defn- daily-event [state]
  (when (and (pos? (:tick state)) (zero? (mod (:tick state) daily-ticks)))
    (let [players (:players state)
          leaders [(apply max-key :money players)
                   (apply max-key :prestige players)
                   (apply max-key total-regulars players)]
          leader (nth leaders (mod (quot (:tick state) daily-ticks) (count leaders)))]
      [{:kind :daily-chronicle
        :message (case (mod (quot (:tick state) daily-ticks) 3)
                   0 (str "Abendblatt: " (:name leader) " sitzt auf der vollsten Kasse der Stadt. "
                          "Der Tresor beantragt schon Überstunden.")
                   1 (str "Abendblatt: " (:name leader) " führt beim Prestige. "
                          "Ein Passant wollte ein Autogramm auf seine Serviette.")
                   (str "Abendblatt: " (:name leader) " kennt inzwischen " (total-regulars leader)
                        " Stammgäste. Die Schlange grüßt sich bereits mit Vornamen."))}])))

(defn events
  "Creates public, durable city-chronicle events for one completed tick."
  [before after actions]
  (vec (concat (action-events before actions)
               (audit-events before after)
               (regular-events before after)
               (city-events before after)
               (daily-event after))))

(comment
  ;; (events before-state after-state scheduled-actions)
  )
