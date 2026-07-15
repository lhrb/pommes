(ns dev.lhrb.pommes.pommes-connection.game-tick
  (:require
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]
   [next.jdbc :as jdbc]))

(def default-simulation-config
  (game-data/simulation-config))

(defn- setup-dishes
  [dishes]
  (mapv (fn [index dish]
          (assoc dish :id (or (:id dish) (str "setup-dish-" index))))
        (range)
        dishes))

(defn- apply-action
  [state {:keys [game-player-id kind payload]}]
  (case kind
    :setup
    (update state :players
            (fn [players]
              (mapv (fn [player]
                      (if (= game-player-id (:id player))
                        (-> player
                            (assoc :interior (:interior payload))
                            (assoc :dishes (setup-dishes (:dishes payload))))
                        player))
                    players)))
    (throw (ex-info "Unknown scheduled action" {:kind kind}))))

(defn- tick-summary
  [state]
  {:tick (:tick state)
   :players
   (mapv (fn [{:keys [id money prestige last-tick]}]
           {:player-id id
            :sales (:sales last-tick)
            :revenue (:revenue last-tick)
            :profit (:profit last-tick)
            :demographic-sales (:demographic-sales last-tick)
            :money money
            :prestige prestige})
         (:players state))})

(defn run-next-tick!
  ([datasource game-id]
   (run-next-tick! datasource game-id default-simulation-config))
  ([datasource game-id simulation-config]
   (jdbc/with-transaction [tx datasource]
     (game-repository/enable-foreign-keys! tx)
     (let [state (game-repository/load-game-state tx game-id simulation-config)
           actions (game-repository/load-actions tx game-id (inc (:tick state)))
           state-with-actions (reduce apply-action state actions)
           updated-state (simulation/simulate-tick state-with-actions)
           summary (tick-summary updated-state)]
       (game-repository/persist-tick! tx updated-state (mapv :id actions) summary)
       summary))))

(comment
  ;; (run-next-tick! datasource game-id)
  )
