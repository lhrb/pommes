(ns dev.lhrb.pommes.pommes-connection.action-simulation
  (:require
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]))

(defn action-prestige
  [value cost target-strength]
  (let [ratio (if (pos? (double cost)) (/ (double value) (double cost)) 0.0)
        base (cond
               (< ratio 0.5) 0.0
               (< ratio 1.0) 1.0
               (< ratio 2.0) 2.0
               :else 3.0)]
    (long (Math/round (min 4.0 (* base (double target-strength)))))))

(defn- action-uses
  [state]
  (->> (:players state)
       (mapcat :effects)
       (filter :action-use-id)
       (group-by :action-use-id)
       (mapv (fn [[id effects]]
               (merge (select-keys (first effects)
                                   [:actor-player-id :target-player-id :action-id
                                    :action-cost :target-strength])
                      {:action-use-id id
                       :expires? (some #(= 1 (:ticks-left %)) effects)})))))

(defn- without-action
  [state action-use-id]
  (update state :players
          (fn [players]
            (mapv #(update % :effects
                           (fn [effects]
                             (filterv (comp not #{action-use-id} :action-use-id) effects)))
                  players))))

(defn- player-profit
  [state player-id]
  (double (or (some #(when (= player-id (:id %)) (get-in % [:last-tick :profit]))
                    (:players state))
              0.0)))

(defn- add-action-value
  [state main {:keys [action-use-id actor-player-id target-player-id action-cost
                      target-strength action-id expires?]}]
  (let [counterfactual (simulation/simulate-tick (without-action state action-use-id))
        actual-profit (player-profit main target-player-id)
        counterfactual-profit (player-profit counterfactual target-player-id)
        marginal (max 0.0 (if (= actor-player-id target-player-id)
                            (- actual-profit counterfactual-profit)
                            (- counterfactual-profit actual-profit)))]
    (update main :players
            (fn [players]
              (mapv (fn [player]
                      (if (not= actor-player-id (:id player))
                        player
                        (let [previous (double (get-in player [:game-stats :action-values action-use-id :value] 0.0))
                              value (+ previous marginal)
                              award (if expires?
                                      (action-prestige value action-cost (or target-strength 1.0))
                                      0)]
                          (cond-> (assoc-in player [:game-stats :action-values action-use-id]
                                            {:action-id action-id :target-player-id target-player-id
                                             :cost action-cost :value value})
                            expires? (update-in [:game-stats :action-values] dissoc action-use-id)
                            expires? (assoc-in [:game-stats :latest-action-result]
                                               {:action-id action-id :target-player-id target-player-id
                                                :cost action-cost :value value :prestige award})
                            (pos? award) (update :prestige (fnil + 0) award)
                            (pos? award) (update-in [:game-stats :prestige-by-source :actions]
                                                   (fnil + 0) award)))))
                    players)))))

(defn simulate-tick
  "Runs the live tick and attributes the marginal value of every stacked action use."
  [state]
  (let [uses (action-uses state)
        main (simulation/simulate-tick state)]
    (reduce #(add-action-value state %1 %2) main uses)))

(comment
  (action-prestige 150.0 100.0 1.25))
