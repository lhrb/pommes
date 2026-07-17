(ns dev.lhrb.pommes.pommes-connection.game-tick
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-log :as game-log]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]
   [next.jdbc :as jdbc]))

(def default-simulation-config
  (game-data/simulation-config))

(defn- tick-millis
  [simulation-config]
  (* 1000 (get-in simulation-config [:rules :tick-seconds])))

(defn- setup-dishes
  [dishes]
  (mapv (fn [index dish]
          (assoc dish :id (or (:id dish) (str "setup-dish-" index))))
        (range)
        dishes))

(defn- apply-action
  [state {:keys [game-player-id kind payload]}]
  (cond
    (= kind :setup)
    (update state :players
            (fn [players]
              (mapv (fn [player]
                      (if (= game-player-id (:id player))
                        (-> player
                            (assoc :interior (:interior payload))
                            (assoc :dishes (setup-dishes (:dishes payload))))
                        player))
                    players)))

    (str/starts-with? (name kind) "action-")
    (let [action (game-data/action (:action-id payload))]
      (when-not action
        (throw (ex-info "Unknown game action" {:action-id (:action-id payload)})))
      (update state :players
              (fn [players]
                (mapv (fn [player]
                        (if (= (:target-game-player-id payload) (:id player))
                          (update player :effects into
                                  (mapv #(assoc %
                                                :ticks-left (:duration action)
                                                :action-id (:id action)
                                                :action-use-id (:action-use-id payload))
                                        (:effects action)))
                          player))
                      players))))

    :else
    (throw (ex-info "Unknown scheduled action" {:kind kind}))))

(defn- tick-summary
  [state]
  {:tick (:tick state)
   :demographic-unserved-demand
   (into {}
         (map (juxt :demographic-id :unserved-demand)
              (:last-market state)))
   :players
   (mapv (fn [{:keys [id money prestige regulars last-tick]}]
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
            :demographic-sales (:demographic-sales last-tick)
            :money money
            :prestige prestige
            :regulars regulars})
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
           updated-state (assoc (simulation/simulate-tick state-with-actions)
                                :next-tick-at (+ (:next-tick-at state)
                                                 (tick-millis simulation-config)))
           summary (tick-summary updated-state)
           events (game-log/events state updated-state actions)]
       (game-repository/persist-tick! tx updated-state (mapv :id actions) summary events)
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
