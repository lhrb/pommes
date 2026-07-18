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

(defn- choose-message
  "Picks a stable voice for an event, so replaying a tick writes the same chronicle."
  [state event-key messages]
  (nth messages (Math/floorMod (hash [(:tick state) event-key]) (count messages))))

(defn- action-events [state action-uses]
  (let [players (player-by-id state)]
    (keep (fn [{:keys [id actor-game-player-id target-game-player-id action-id]}]
            (let [action (game-data/action (keyword action-id))
                  actor (get players actor-game-player-id)
                  target (get players target-game-player-id)]
                {:kind :action
                 :message
                 (choose-message
                  state [id action-id]
                  (case (:id action)
                    :send-punks
                    [(str (:name actor) " schickte Punks zu " (:name target)
                          ". Seitdem wird dort mit offenem Fenster nur noch aus Überzeugung gegessen.")
                     (str "Bei " (:name target) " erschienen Punks auf Veranlassung von " (:name actor)
                          ". Der Kiosk gegenüber führt Ohrstöpsel nun unter Grundbedarf.")
                     (str (:name actor) " ließ Punks bei " (:name target) " vorfahren. "
                          "Die Nachbarschaft nennt es eine Frage der Jugend, niemand kennt die Frage.")]

                    :rocker-meeting
                    [(str (:name actor) " berief vor " (:name target) " ein Rockertreffen ein. "
                          "Die Maschinen standen ordentlich; der Bürgersteig nicht.")
                     (str "Vor " (:name target) " wurde es laut, weil " (:name actor)
                          " Motorräder für einen guten Eindruck hielt. Die Leute blieben trotzdem stehen.")
                     (str "Das Rockertreffen von " (:name actor) " erreichte " (:name target)
                          ". Ein Anwohner beantragte Ruhezeiten, die Fahrer beantragten Pommes.")]

                    :influencer-visit
                    [(str (:name actor) " brachte ein Ringlicht zu " (:name target)
                          ". Nun gilt dort jede Portion als mögliches Zeugnis.")
                     (str "Bei " (:name target) " wurde auf Betreiben von " (:name actor)
                          " gefilmt. Die Sauce bekam mehrere Einstellungen und keine Erklärung.")
                     (str (:name actor) " schickte einen Influencer zu " (:name target)
                          ". Die Wartenden prüften ihre Frisuren und bestellten dann wie immer.")]

                    :parking-chaos
                    [(str (:name actor) " ordnete vor " (:name target) " neue Parkverhältnisse an. "
                          "Ein Kombi steht seither aus Prinzip quer.")
                     (str "Vor " (:name target) " herrscht Parkplatzchaos, seit " (:name actor)
                          " sich der Sache angenommen hat. Niemand will Schuld sein, alle wollen weg.")
                     (str (:name actor) " ließ die Autos bei " (:name target) " neu sortieren. "
                          "Die Familien beraten nun länger über den Weg als über die Portion.")]

                    :review-bomb
                    [(str (:name actor) " ließ Erfahrungsberichte über " (:name target)
                          " verbreiten. Die Fritteuse wurde darin als \"ambitioniert\" beschrieben.")
                     (str "Auf Betreiben von " (:name actor) " bekam " (:name target)
                          " auffällig viele Bewertungen. Ein Stammgast verlangt jetzt eine Gegendarstellung.")
                     (str (:name actor) " setzte " (:name target) " in die Sterne. "
                          "Die Nachbarschaft liest aufmerksam und versteht nur die Ausrufezeichen.")]

                    :neighborhood-festival
                    [(str (:name actor) " veranstaltete bei " (:name target) " ein Nachbarschaftsfest. "
                          "Die Gabeln reichten nicht, aber die Meinungen reichten bis zum Abend.")
                     (str "Das Fest von " (:name actor) " zog bei " (:name target)
                          " ein. Jemand übernahm die Musik und damit eine Verantwortung, die ihm nicht zustand.")
                     (str (:name actor) " rief bei " (:name target) " zum Nachbarschaftsfest. "
                          "Am Ende waren alle versöhnt, bis auf den Mann mit der letzten Sitzbank.")]

                    [(str (:name actor) " löste bei " (:name target)
                          " eine Angelegenheit aus, über die die Stadt vorerst nur hinter vorgehaltener Hand spricht.")]))}))
          action-uses)))

(defn- audit-events [_before after]
  (keep (fn [player]
          (when (get-in player [:last-tick :audited?])
            {:kind :audit
             :message
             (choose-message
              after [(:id player) (get-in player [:game-stats :audit-count])]
              [(str "Zur Betriebsprüfung Nummer " (get-in player [:game-stats :audit-count])
                    " erschien das Finanzamt bei " (:name player)
                    ". Die Kasse musste Auskunft geben, die Speisekarte wartete draußen.")
               (str "Bei " (:name player) " wurde erneut geprüft. Die Beamten sprachen von Ordnung; "
                    "der Schichtleiter sprach von einem Missverständnis mit mehreren Seiten.")
               (str "Die Prüfung bei " (:name player)
                    " endete ohne sichtbaren Frieden. Ein Prüfer notierte die Summe, als hätte sie ihn persönlich enttäuscht.")])}))
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
                 :message
                 (choose-message
                  after [(:id player) milestone demographic]
                  [(str (:name player) " zählt nun " milestone " Stammgäste. "
                        (label demographic) " kennen den Weg inzwischen besser als den Grund dafür.")
                   (str milestone " Menschen kommen bei " (:name player) " wieder. "
                        (label demographic) " behandeln die Bude damit als eine örtliche Einrichtung.")
                   (str "Die " milestone "-Stammgastmarke fiel bei " (:name player) ". "
                        (label demographic) " geben die Neuigkeit weiter, mit den nötigen Ungenauigkeiten.")])}))))
        (:players after)))

(defn- city-events [before after]
  (when-let [milestone (crossed-milestone (get-in before [:city :population] 0)
                                          (get-in after [:city :population] 0)
                                          city-milestones)]
    [{:kind :city-growth
      :message
      (choose-message
       after [:city-growth milestone]
       [(str "Die Stadt zählt nun " milestone " Einwohner. Das Rathaus spricht von Wachstum; "
             "die Buden sprechen von einer zusätzlichen Schicht.")
        (str "Mit " milestone " Einwohnern ist die Stadt größer geworden. "
             "Die Warteschlangen nehmen dies als persönlichen Auftrag entgegen.")
        (str "Die Marke von " milestone " Einwohnern ist erreicht. "
             "Jemand beantragt bereits eine weitere Bank vor der Fritteuse.")])}]))

(defn- daily-event [state]
  (when (and (pos? (:tick state)) (zero? (mod (:tick state) daily-ticks)))
    (let [players (:players state)
          leaders [(apply max-key :money players)
                   (apply max-key :prestige players)
                   (apply max-key total-regulars players)]
          leader (nth leaders (mod (quot (:tick state) daily-ticks) (count leaders)))]
      [{:kind :daily-chronicle
        :message
        (case (mod (quot (:tick state) daily-ticks) 3)
          0 (choose-message
             state [:daily :money (:id leader)]
             [(str "Abendblatt: Bei " (:name leader) " liegt heute das meiste Geld in der Kasse. "
                   "Man bewacht es mit der Würde einer kleinen Behörde.")
              (str "Abendblatt: " (:name leader) " führt die Kassen der Stadt an. "
                   "Der Wechselgeldbecher hat sich seither merklich zurückhaltender gegeben.")
              (str "Abendblatt: Die vollste Kasse steht bei " (:name leader) ". "
                   "Ein Nachbar nennt es Erfolg, der Inhaber nennt es Dienstag.")])
          1 (choose-message
             state [:daily :prestige (:id leader)]
             [(str "Abendblatt: " (:name leader) " genießt das höchste Ansehen der Stadt. "
                   "Die Servietten werden dort vorsichtiger gefaltet.")
              (str "Abendblatt: Beim Prestige liegt " (:name leader) " vorn. "
                   "Ein Passant nahm die Schlange als Empfehlung und stellte sich an.")
              (str "Abendblatt: Die Stadt spricht gut über " (:name leader) ". "
                   "Niemand weiß genau warum, aber die meisten finden es angemessen.")])
          (choose-message
           state [:daily :regulars (:id leader)]
           [(str "Abendblatt: " (:name leader) " kennt inzwischen " (total-regulars leader)
                 " Stammgäste. Die Schlange erledigt Begrüßungen inzwischen ohne Anleitung.")
            (str "Abendblatt: Bei " (:name leader) " kehren " (total-regulars leader)
                 " Menschen regelmäßig zurück. Das gilt in dieser Stadt beinahe als Vertrag.")
            (str "Abendblatt: " (:name leader) " hat " (total-regulars leader)
                 " Stammgäste. Einige kennen die Speisekarte, andere nur ihren festen Platz davor.")]))}])))

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
