(ns dev.lhrb.pommes.pommes-connection.dish-market
  "Pure market projections for the dish editor."
  (:require
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]))

(defn- player-allocation
  [result player-id]
  (some #(when (= player-id (:player-id %)) %) (:allocations result)))

(defn- replace-setup
  [state player-id setup]
  (update state :players
          (fn [players]
            (mapv (fn [player]
                    (if (= player-id (:id player))
                      (assoc player
                             :interior (:interior setup)
                             :dishes (mapv (fn [index dish]
                                             (assoc dish :id (str "forecast-dish-" index)))
                                           (range)
                                           (:dishes setup)))
                      player))
                  players))))

(defn forecast
  "Compares the current menu with a proposed setup for one player.

  Sales and shares are the next tick's deterministic projection; nothing is
  persisted by this function."
  [state player-id setup]
  (let [current (simulation/simulate-tick state)
        draft (simulation/simulate-tick (replace-setup state player-id setup))
        current-results (into {} (map (juxt :demographic-id identity) (:last-market current)))
        draft-results (into {} (map (juxt :demographic-id identity) (:last-market draft)))
        draft-dishes (into {} (map-indexed (fn [index dish]
                                              [(str "forecast-dish-" index) dish])
                                            (:dishes setup)))]
    (mapv (fn [demographic]
            (let [id (:id demographic)
                  before (player-allocation (get current-results id) player-id)
                  after (player-allocation (get draft-results id) player-id)
                  dish (get draft-dishes (:dish-id after))]
              {:demographic-id id
               :potential-demand (:potential-demand (get draft-results id))
               :dish-name (:name dish)
               :sales (:sales after 0)
               :share (:share after 0.0)
               :sales-change (- (:sales after 0) (:sales before 0))
               :share-change (- (:share after 0.0) (:share before 0.0))}))
          (get-in state [:city :demographics]))))

(comment
  ;; (forecast game-state player-id {:interior :classic :dishes [...]})
  )
