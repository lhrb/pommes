(ns dev.lhrb.pommes.pommes-connection.balancing-simulation
  "Pure seven-day balance experiments for three-player games."
  (:import
   [java.util.concurrent Executors])
  (:require
   [dev.lhrb.pommes.pommes-connection.action-simulation :as action-simulation]
   [dev.lhrb.pommes.pommes-connection.demographics-simulation :as demographics]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.game-rules :as game-rules]
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]))

(def strategies
  [:adaptive-menu :research :black-market :actions :regulars :hoarder :random])

(def activity-levels [2 4 8])

(def fast-step-ticks 180)

(def menu-profiles
  {:students {:interior :street-food
              :dishes [{:name "Die Günstige" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 350}
                       {:name "Die Würzige" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :curry-ketchup :price 450}
                       {:name "Die Große" :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 520}]}
   :families {:interior :cozy
              :dishes [{:name "Familienklein" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 420}
                       {:name "Familiengroß" :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 550}
                       {:name "Familienamt" :pommes {:size :extra-large :seasoning :salt} :topping :mayo :price 620}]}
   :office-workers {:interior :modern
                    :dishes [{:name "Mittag" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :curry-ketchup :price 520}
                             {:name "Termin" :pommes {:size :small :seasoning :salt} :topping :ketchup :price 450}
                             {:name "Überstunde" :pommes {:size :large :seasoning :pommesgewuerz} :topping :garlic-sauce :price 620}]}
   :premium {:interior :premium
             :dishes [{:name "Besonderheit" :pommes {:size :small :seasoning :pommesgewuerz} :topping :special :price 850}
                      {:name "Satefrage" :pommes {:size :small :seasoning :pommesgewuerz} :topping :sate :price 800}
                      {:name "Knoblauchlage" :pommes {:size :medium :seasoning :pommesgewuerz} :topping :garlic-sauce :price 720}]}})

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
                             :prestige 0
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
                              (update-in [:game-stats :spending system] (fnil + 0) cost))
                          player))
                      players))))))

(defn- leading-opponent [state player-id]
  (:id (first (sort-by (juxt (comp - :prestige) :id)
                       (remove #(= player-id (:id %)) (:players state))))))

(defn- use-action [state player-id action-id]
  (let [player (some #(when (= player-id (:id %)) %) (:players state))
        action (game-data/action action-id)
        tick (:tick state)
        {:keys [maximum-uses reset-ticks]} (game-rules/action-usage-rules state)
        uses (filter #(> % (- tick reset-ticks)) (get-in player [:game-stats :action-purchases] []))
        cost (game-rules/action-cost state action tick)
        target-id (if (= :any-player (:target action)) player-id (leading-opponent state player-id))
        use-id (str (name player-id) "-" tick "-" (name action-id))]
    (if (or (>= (count uses) maximum-uses)
            (< (:money player) cost))
      state
      (update state :players
              (fn [players]
                (mapv (fn [candidate]
                        (cond-> candidate
                          (= player-id (:id candidate))
                          (-> (update :money - cost)
                              (assoc-in [:game-stats :action-purchases] (conj (vec uses) tick))
                              (update-in [:game-stats :spending :actions] (fnil + 0) cost))

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

(defn- advance-effects [state ticks]
  (update state :players
          (fn [players]
            (mapv (fn [player]
                    (-> player
                        (update :effects
                                (fn [effects]
                                  (->> effects
                                       (map (fn [effect]
                                              (update effect :ticks-left (fnil - 0) ticks)))
                                       (filter (comp pos? :ticks-left))
                                       vec)))
                        (update :game-stats
                                (fn [stats]
                                  (update (or stats {}) :audit-penalty-ticks
                                          (fn [remaining]
                                            (max 0 (- (long (or remaining 0)) ticks))))))))
                  players))))

(defn- fast-rules [state ticks]
  (let [rules (:rules state)
        regulars (:regulars state)
        black-market (:black-market state)
        alpha (double (:market-memory-alpha rules 0.10))
        decay (double (:suspicion-decay black-market 1.0))
        decay-over-step (Math/pow decay ticks)
        gain (double (:suspicion-gain-per-concealment black-market 0.0))
        scaled-gain (if (or (zero? gain) (= decay 1.0))
                      (* gain ticks)
                      (/ (* gain decay (- 1.0 decay-over-step))
                         (* decay-over-step (- 1.0 decay))))]
    {:rules (assoc rules
                   :demand-ticks ticks
                   :market-memory-alpha (- 1.0 (Math/pow (- 1.0 alpha) ticks)))
     :regulars (assoc regulars
                      :loss-rate (- 1.0 (Math/pow (- 1.0 (double (:loss-rate regulars 0.0))) ticks)))
     :black-market (assoc black-market
                          :suspicion-decay decay-over-step
                          :suspicion-gain-per-concealment scaled-gain)}))

(defn simulate-fast-ticks
  "Advances a balance-only approximation by ticks. It preserves the real
  simulation's rules, but aggregates demand and time-based effects into one
  call, so it is intentionally not equivalent to repeated live ticks."
  [state ticks]
  (let [{:keys [rules regulars black-market]} state
        scaled (fast-rules state ticks)
        prepared (-> state
                     (assoc :tick (+ (:tick state) ticks -1)
                            :rules (:rules scaled)
                            :regulars (:regulars scaled)
                            :black-market (:black-market scaled))
                     (advance-effects (dec ticks)))
        next (action-simulation/simulate-tick prepared)]
    (assoc next :rules rules :regulars regulars :black-market black-market)))

(defn- next-step-size [state ticks mode]
  (case mode
    :fast (min fast-step-ticks (- ticks (:tick state)))
    :exact 1
    (throw (ex-info "Unknown balancing simulation mode" {:mode mode}))))

(defn run-game
  [{:keys [agents seed ticks mode include-final-state?]
    :or {agents [{:strategy :adaptive-menu :activity 4}
                 {:strategy :research :activity 4}
                 {:strategy :actions :activity 4}]
         seed 1 ticks 20160 mode :fast}}]
  (let [report-at (conj #{2880 11520 ticks}
                        (get-in game-data/game-data [:game :score-every-ticks]))]
    (loop [state (initial-state agents seed)
           history []
           previous-leader nil
           leader-changes 0]
      (if (>= (:tick state) ticks)
        (let [ranking (sort-by (juxt (comp - :prestige) :id) (:players state))]
          (cond-> {:seed seed :agents agents
                   :history (cond-> history
                              (or (empty? history) (not= (:tick (peek history)) (:tick state)))
                              (conj (snapshot state)))
                   :leader-changes leader-changes
                   :winner (select-keys (first ranking) [:id :strategy :activity :prestige])}
            include-final-state? (assoc :final-state state)))
        (let [prepared (decisions state)
              step-size (next-step-size state ticks mode)
              next (if (= mode :exact)
                     (action-simulation/simulate-tick prepared)
                     (simulate-fast-ticks prepared step-size))
              leader (->> (:players next)
                          (apply max-key :prestige)
                          :id)
              changed? (and previous-leader
                            (not= previous-leader leader))
              history (if (or (contains? report-at (:tick next))
                              (zero? (mod (:tick next) 720)))
                        (conj history (snapshot next)) history)]
          (recur next history leader (cond-> leader-changes changed? inc)))))))

(defn run-suite
  [{:keys [seeds mode parallelism include-final-state? ticks]
    :or {seeds (range 1 31) mode :fast}}]
  (let [seeds (vec seeds)
        game-input (fn [seed]
                     (let [offset (mod seed (count strategies))]
                       (cond-> {:agents (mapv (fn [index]
                                                {:strategy (nth strategies (mod (+ offset index) (count strategies)))
                                                 :activity (nth activity-levels (mod (+ seed index) (count activity-levels)))})
                                              (range 3))
                                :seed seed :mode mode
                                :include-final-state? include-final-state?}
                         ticks (assoc :ticks ticks))))
        worker-count (max 1 (min (count seeds)
                                 (or parallelism (.availableProcessors (Runtime/getRuntime)))))
        executor (Executors/newFixedThreadPool worker-count)]
    (try
      (let [tasks (mapv #(.submit executor ^Callable (reify java.util.concurrent.Callable
                                                        (call [_] (run-game (game-input %)))))
                        seeds)]
        (mapv #(.get %) tasks))
      (finally
        (.shutdown executor)))))

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
  ;; Fast mode is the default while changing population, appetite, or costs.
 (def one-week (run-game {:seed 42}))
 (def one-week-exact (run-game {:seed 42 :mode :exact}))

 (-> one-week :history last)
 (-> one-week-exact :history last)

 (def l (-> one-week :history last))

 (time (run-game {:seed 42}))
  ;; Confirm a result with the real single-tick simulation.
 (time (run-game {:seed 42 :mode :exact}))

 (map
  #(select-keys % [:prestige :strategy :activity])
  (-> one-week-exact :history last :players))

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

  ;; The default suite is fast and parallel. Ask for final state only when debugging.
 (def suite (run-suite {:seeds (range 1 31)}))
 (def exact-suite (run-suite {:seeds (range 1 4) :mode :exact :parallelism 1}))
 (run-game {:seed 42 :include-final-state? true})
 (def report (suite-report suite))
 (acceptance-failures report))
