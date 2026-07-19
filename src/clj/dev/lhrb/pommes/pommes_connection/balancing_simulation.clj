(ns dev.lhrb.pommes.pommes-connection.balancing-simulation
  "Pure seven-day balance experiments for three-player games."
  (:require
   [dev.lhrb.pommes.pommes-connection.action-simulation :as action-simulation]
   [dev.lhrb.pommes.pommes-connection.demographics-simulation :as demographics]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.game-rules :as game-rules]
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]))

(def strategies
  [:adaptive-menu :research :black-market :actions :regulars :hoarder :random])

(def activity-levels [2 4 8])

(def menu-profiles
  {:students {:interior :street-food
              :dishes [{:name "Die Günstige" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 3.50}
                       {:name "Die Würzige" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :curry-ketchup :price 4.50}
                       {:name "Die Große" :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 5.20}]}
   :families {:interior :cozy
              :dishes [{:name "Familienklein" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 4.20}
                       {:name "Familiengroß" :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 5.50}
                       {:name "Familienamt" :pommes {:size :extra-large :seasoning :salt} :topping :mayo :price 6.20}]}
   :office-workers {:interior :modern
                    :dishes [{:name "Mittag" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :curry-ketchup :price 5.20}
                             {:name "Termin" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 4.50}
                             {:name "Überstunde" :pommes {:size :large :seasoning :pommesgewuerz} :topping :garlic-sauce :price 6.20}]}
   :premium {:interior :premium
             :dishes [{:name "Besonderheit" :pommes {:size :small :seasoning :pommesgewuerz} :topping :special :price 8.50}
                      {:name "Satefrage" :pommes {:size :small :seasoning :pommesgewuerz} :topping :sate :price 8.00}
                      {:name "Knoblauchlage" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :garlic-sauce :price 7.20}]}})

(defn- with-dish-ids [profile player-id]
  (update profile :dishes
          #(mapv (fn [index dish] (assoc dish :id (str (name player-id) "-" index)))
                 (range) %)))

(defn initial-state
  ([agents] (initial-state agents 1))
  ([agents seed]
   (let [config (game-data/simulation-config)
         starting-money (get-in config [:economy :starting-money])]
     (merge config
            {:tick 0
             :game-id seed
             :game-status :running
             :city (demographics/initial-city (:city config) seed)
             :players
             (mapv (fn [index {:keys [strategy activity]}]
                     (merge {:id (keyword (str "bude-" (inc index)))
                             :name (str "Bude " (inc index))
                             :strategy strategy
                             :activity activity
                             :money starting-money
                             :prestige 0.0
                             :effects [] :market-modifiers {}
                             :regulars {} :research {} :black-market {}
                             :game-stats {}}
                            (with-dish-ids (get menu-profiles
                                                 (nth [:students :families :premium] index))
                                           (keyword (str "bude-" (inc index))))))
                   (range) agents)}))))

(defn- decision-due? [tick activity player-index]
  (let [interval (quot 2880 activity)]
    (zero? (mod (+ tick (* player-index (quot interval 3))) interval))))

(defn- dominant-demographic [state]
  (:id (apply max-key :population (get-in state [:city :demographics]))))

(defn- adaptive-profile [state]
  (let [demographic (dominant-demographic state)]
    (cond
      (#{:yuppies :foodies} demographic) :premium
      (= :families demographic) :families
      (= :office-workers demographic) :office-workers
      :else :students)))

(defn- buy-level [state player-id system branch]
  (let [level (long (get-in (some #(when (= player-id (:id %)) %) (:players state))
                            [system branch] 0))
        cost (game-rules/progression-cost state system level)]
    (if (nil? cost)
      state
      (update state :players
              (fn [players]
                (mapv (fn [player]
                        (if (and (= player-id (:id player)) (>= (:money player) cost))
                          (-> player
                              (update :money - cost)
                              (update-in [system branch] (fnil inc 0))
                              (update-in [:game-stats :spending system] (fnil + 0.0) cost))
                          player))
                      players))))))

(defn- leading-opponent [state player-id]
  (:id (first (sort-by (juxt (comp - :prestige) :id)
                       (remove #(= player-id (:id %)) (:players state))))))

(defn- use-action [state player-id action-id]
  (let [player (some #(when (= player-id (:id %)) %) (:players state))
        action (game-data/action action-id)
        tick (:tick state)
        window (get-in state [:game :action-window-ticks])
        uses (filter #(> % (- tick window)) (get-in player [:game-stats :action-purchases] []))
        cost (game-rules/action-cost state action tick)
        target-id (if (= :any-player (:target action)) player-id (leading-opponent state player-id))
        use-id (str (name player-id) "-" tick "-" (name action-id))]
    (if (or (>= (count uses) (get-in state [:game :actions-per-window]))
            (< (:money player) cost))
      state
      (update state :players
              (fn [players]
                (mapv (fn [candidate]
                        (cond-> candidate
                          (= player-id (:id candidate))
                          (-> (update :money - cost)
                              (assoc-in [:game-stats :action-purchases] (conj (vec uses) tick))
                              (update-in [:game-stats :spending :actions] (fnil + 0.0) cost))

                          (= target-id (:id candidate))
                          (update :effects into
                                  (mapv #(assoc % :ticks-left (:duration action)
                                                  :action-id action-id
                                                  :action-use-id use-id
                                                  :actor-player-id player-id
                                                  :target-player-id target-id
                                                  :action-cost cost
                                                  :target-strength 1.25)
                                        (:effects action)))))
                      players))))))

(defn- set-menu [state player-id profile-id]
  (update state :players
          (fn [players]
            (mapv #(if (= player-id (:id %))
                     (merge % (with-dish-ids (get menu-profiles profile-id) player-id))
                     %)
                  players))))

(defn- decide [state player-index]
  (let [{:keys [id strategy]} (nth (:players state) player-index)
        random-choice (nth strategies (Math/floorMod
                                       (long (hash [(:game-id state) (:tick state) id]))
                                       (count strategies)))]
    (case strategy
      :adaptive-menu (set-menu state id (adaptive-profile state))
      :research (buy-level state id :research :potato-quality)
      :black-market (buy-level state id :black-market :creative-bookkeeping)
      :actions (use-action state id (if (even? (quot (:tick state) 720))
                                      :neighborhood-festival
                                      :review-bomb))
      :regulars (buy-level state id :research :fryer)
      :hoarder state
      :random (case random-choice
                :adaptive-menu (set-menu state id (adaptive-profile state))
                :research (buy-level state id :research :ketchup-recipe)
                :black-market (buy-level state id :black-market :cash-only-register)
                :actions (use-action state id :neighborhood-festival)
                state)
      state)))

(defn- decisions [state]
  (reduce (fn [current index]
            (if (decision-due? (:tick state) (:activity (nth (:players state) index)) index)
              (decide current index)
              current))
          state (range (count (:players state)))))

(defn- snapshot [state]
  {:tick (:tick state)
   :phase (game-rules/game-phase (:tick state))
   :population (get-in state [:city :population])
   :players (mapv (fn [player]
                    {:id (:id player) :strategy (:strategy player) :activity (:activity player)
                     :money (:money player) :prestige (:prestige player)
                     :prestige-by-source (get-in player [:game-stats :prestige-by-source])
                     :research (:research player) :black-market (:black-market player)
                     :regulars (reduce + 0 (vals (:regulars player)))
                     :sales (reduce + 0 (vals (get-in player [:game-stats :demographic-sales])))
                     :spending (get-in player [:game-stats :spending])
                     :audits (get-in player [:game-stats :audit-count] 0)})
                  (:players state))})

(defn run-game
  [{:keys [agents seed ticks]
    :or {agents [{:strategy :adaptive-menu :activity 4}
                 {:strategy :research :activity 4}
                 {:strategy :actions :activity 4}]
         seed 1 ticks 20160}}]
  (let [report-at (conj #{2880 11520 ticks}
                        (get-in game-data/game-data [:game :score-every-ticks]))]
    (loop [state (initial-state agents seed) history [] leaders []]
      (if (>= (:tick state) ticks)
        (let [ranking (sort-by (juxt (comp - :prestige) :id) (:players state))]
          {:seed seed :agents agents :history (conj history (snapshot state))
           :leaders leaders :leader-changes (count (partition-by identity leaders))
           :winner (select-keys (first ranking) [:id :strategy :activity :prestige])
           :final-state state})
        (let [prepared (decisions state)
              next (action-simulation/simulate-tick prepared)
              leader (:id (first (sort-by (juxt (comp - :prestige) :id) (:players next))))
              history (if (or (contains? report-at (:tick next))
                              (zero? (mod (:tick next) 720)))
                        (conj history (snapshot next)) history)]
          (recur next history (conj leaders leader)))))))

(defn run-suite
  [{:keys [seeds] :or {seeds (range 1 31)}}]
  (mapv (fn [seed]
          (let [offset (mod seed (count strategies))
                agents (mapv (fn [index]
                               {:strategy (nth strategies (mod (+ offset index) (count strategies)))
                                :activity (nth activity-levels (mod (+ seed index) (count activity-levels)))})
                             (range 3))]
            (run-game {:agents agents :seed seed})))
        seeds))

(defn suite-report [games]
  {:games (count games)
   :win-rate (->> games (map (comp :strategy :winner)) frequencies
                  (map (fn [[strategy wins]] [strategy (/ (double wins) (count games))]))
                  (into {}))
   :average-leader-changes (if (seq games)
                             (/ (reduce + (map :leader-changes games)) (double (count games)))
                             0.0)
   :finals (mapv #(-> % :history last :players) games)})

(defn acceptance-failures [report]
  (cond-> []
    (some #(> % 0.40) (vals (:win-rate report)))
    (conj :strategy-over-forty-percent)
    (< (:average-leader-changes report) 2.0)
    (conj :too-few-leader-changes)))

(comment
  ;; One quick game while changing population, appetite, or costs.
  (def one-week (run-game {:seed 42}))
  (-> one-week :history last)

  ;; Compare two, four, and eight daily decisions.
  (for [activity activity-levels]
    (:winner (run-game {:seed activity
                        :agents (mapv #(hash-map :strategy % :activity activity)
                                      [:adaptive-menu :research :actions])})))

  ;; A coordinated pile-on remains legal: use-action adds another action-use
  ;; even while equivalent effects are already active.
  (-> (initial-state [{:strategy :actions :activity 8}
                      {:strategy :actions :activity 8}
                      {:strategy :hoarder :activity 2}] 7)
      (use-action :bude-1 :review-bomb)
      (use-action :bude-2 :review-bomb)
      :players)

  ;; The full fixed-seed suite is intentionally explicit and may take a while.
  (def suite (run-suite {:seeds (range 1 31)}))
  (def report (suite-report suite))
  (acceptance-failures report))
