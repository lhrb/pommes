(ns dev.lhrb.pommes.pommes-connection.game-tick
  (:require
   [dev.lhrb.pommes.pommes-connection.action-simulation :as action-simulation]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-log :as game-log]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [next.jdbc :as jdbc]))

(def default-simulation-config
  (game-data/simulation-config))

(defn- tick-millis
  [simulation-config]
  (* 1000 (get-in simulation-config [:rules :tick-seconds])))

(defn- target-strength
  [players target-id]
  (let [ranked (mapv :id (sort-by (juxt (comp - :prestige) :id) players))
        rank (.indexOf ranked target-id)]
    (case rank 0 1.5 1 1.25 1.0)))

(defn- apply-action-use
  [state {:keys [id actor-game-player-id target-game-player-id action-id cost]}]
  (let [action (game-data/action (keyword action-id))]
    (when-not action
      (throw (ex-info "Unknown game action" {:action-id action-id})))
    (update state :players
            (fn [players]
              (mapv (fn [player]
                      (if (= target-game-player-id (:id player))
                        (update player :effects into
                                (mapv #(assoc %
                                              :ticks-left (:duration action)
                                              :action-id (:id action)
                                              :actor-player-id actor-game-player-id
                                              :target-player-id target-game-player-id
                                              :action-cost cost
                                              :target-strength (target-strength players target-game-player-id)
                                              :action-use-id id)
                                      (:effects action)))
                        player))
                    players)))))

(defn- tick-summary
  [state]
  {:tick (:tick state)
   :demographic-unserved-demand
   (into {}
         (map (juxt :demographic-id :unserved-demand)
              (:last-market state)))
   :players
   (mapv (fn [{:keys [id money prestige regulars last-tick game-stats]}]
           {:player-id id
            :sales (:sales last-tick)
            :regular-sales (:regular-sales last-tick)
            :revenue (:revenue last-tick)
            :gross-profit (:gross-profit last-tick)
            :profit (:profit last-tick)
            :tax (:tax last-tick)
            :tax-avoided (:tax-avoided last-tick)
            :concealment-rate (:concealment-rate last-tick)
            :suspicion (:suspicion last-tick)
            :audited? (:audited? last-tick)
            :audit-penalty-ticks (:audit-penalty-ticks last-tick)
            :dish-sales (:dish-sales last-tick)
            :demographic-sales (:demographic-sales last-tick)
            :money money
            :prestige prestige
            :prestige-by-source (:prestige-by-source game-stats)
            :score-round (:latest-score-round game-stats)
            :action-result (:latest-action-result game-stats)
            :regulars regulars})
         (:players state))})

(defn run-next-tick!
  ([datasource game-id]
   (run-next-tick! datasource game-id default-simulation-config))
  ([datasource game-id simulation-config]
   (jdbc/with-transaction [tx datasource]
     (game-repository/enable-foreign-keys! tx)
     (let [state (game-repository/load-game-state tx game-id simulation-config)
           action-uses (game-repository/load-due-action-uses tx game-id (inc (:tick state)))
           state-with-actions (reduce apply-action-use state action-uses)
           updated-state (assoc (action-simulation/simulate-tick state-with-actions)
                                :next-tick-at (+ (:next-tick-at state)
                                                 (tick-millis simulation-config)))
           summary (tick-summary updated-state)
           events (game-log/events state updated-state action-uses)]
       (game-repository/persist-tick! tx updated-state summary events)
       summary))))

(defn run-due-ticks!
  "Runs up to max-ticks-per-game overdue ticks for every game due at now."
  ([datasource now]
   (run-due-ticks! datasource now default-simulation-config 10))
  ([datasource now simulation-config max-ticks-per-game]
   (mapv (fn [game-id]
           {:game-id game-id
            :summaries
            (loop [summaries []]
              (if (and (< (count summaries) max-ticks-per-game)
                       (game-repository/game-due? datasource game-id now))
                (recur (conj summaries
                             (run-next-tick! datasource game-id simulation-config)))
                summaries))})
         (game-repository/due-game-ids datasource now))))

(comment
  ;; (run-next-tick! datasource game-id)
  ;; (run-due-ticks! datasource (System/currentTimeMillis))
  )
