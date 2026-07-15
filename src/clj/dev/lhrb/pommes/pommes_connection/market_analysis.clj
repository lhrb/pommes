(ns dev.lhrb.pommes.pommes-connection.market-analysis
  "REPL helpers for exploring and balancing the market simulation."
  (:require
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]))

(def report-ticks
  [1 10])

(defn- update-dish
  [state player-id dish-id f]
  (update state
          :players
          (fn [players]
            (mapv (fn [player]
                    (if (= player-id (:id player))
                      (update player
                              :dishes
                              (fn [dishes]
                                (mapv (fn [dish]
                                        (if (= dish-id (:id dish))
                                          (f dish)
                                          dish))
                                      dishes)))
                      player))
                  players))))

(defn- set-dish-price
  [state player-id dish-id price]
  (update-dish state player-id dish-id #(assoc % :price price)))

(defn- set-dish-topping
  [state player-id dish-id topping]
  (update-dish state player-id dish-id #(assoc % :topping topping)))

(def scenarios
  {:baseline
   {:description "The sample game with its normal price anchors and offers."
    :state simulation/sample-state}

   :price-pressure
   {:description "Comparable budget offers force price-sensitive customers to react to price."
    :state (-> simulation/sample-state
               (set-dish-price :fritza :cheap-ketchup 3.10)
               (set-dish-price :goldene-gabel :kids 3.20)
               (set-dish-price :pommes-lab :normal-ketchup 3.30))}

   :premium-status
   {:description "A coherent premium offer competes for the status-oriented yuppie demographic."
    :state (-> simulation/sample-state
               (set-dish-price :pommes-lab :micro-special 8.50)
               (set-dish-price :pommes-lab :sate-edition 8.00)
               (set-dish-price :goldene-gabel :sate 8.50))}

   :variety-collision
   {:description "All players converge on ketchup, revealing topping and positioning variety feedback."
    :state (-> simulation/sample-state
               (set-dish-topping :fritza :cheap-ketchup :ketchup)
               (set-dish-topping :fritza :large-mayo :ketchup)
               (set-dish-topping :fritza :special :ketchup)
               (set-dish-topping :goldene-gabel :classic :ketchup)
               (set-dish-topping :goldene-gabel :kids :ketchup)
               (set-dish-topping :goldene-gabel :sate :ketchup)
               (set-dish-topping :pommes-lab :micro-special :ketchup)
               (set-dish-topping :pommes-lab :sate-edition :ketchup)
               (set-dish-topping :pommes-lab :normal-ketchup :ketchup))}})

(defn scenario
  "Returns a named scenario or throws with the available scenario names."
  [scenario-id]
  (or (get scenarios scenario-id)
      (throw (ex-info "Unknown market scenario"
                      {:scenario-id scenario-id
                       :available-scenarios (keys scenarios)}))))

(defn run-scenario
  "Runs a named scenario and returns its initial state plus every completed tick."
  ([scenario-id]
   (run-scenario scenario-id (apply max report-ticks)))
  ([scenario-id tick-count]
   (let [{:keys [description state]} (scenario scenario-id)]
     {:scenario scenario-id
      :description description
      :states (vec (take (inc tick-count)
                          (iterate simulation/simulate-tick state)))})))

(defn profit-summary
  "Summarizes individual player profit for one completed simulation state."
  [state]
  (let [players
        (mapv (fn [{:keys [id last-tick]}]
                {:player-id id
                 :sales (:sales last-tick 0.0)
                 :revenue (:revenue last-tick 0.0)
                 :profit (:profit last-tick 0.0)})
              (:players state))

        profits
        (mapv :profit players)

        total
        (reduce + 0.0 profits)

        player-count
        (count players)]

    {:tick (:tick state)
     :players players
     :min (if (seq profits) (apply min profits) 0.0)
     :max (if (seq profits) (apply max profits) 0.0)
     :average (simulation/safe-div total player-count)}))

(defn scenario-report
  "Returns tick-one and adapted-market profit summaries for every scenario."
  []
  (mapv (fn [scenario-id]
          (let [{:keys [description]} (scenario scenario-id)
                states (:states (run-scenario scenario-id))]
            {:scenario scenario-id
             :description description
             :rounds (mapv (fn [tick]
                             (profit-summary (nth states tick)))
                           report-ticks)}))
        (keys scenarios)))

(comment
  ;; Inspect the market after a single round of price competition.
  (def price-pressure
    (run-scenario :price-pressure 1))

  ;; See why each demographic chose an offer: utility components, shares,
  ;; sales, and profit are all included in these allocations.
  (-> price-pressure
      :states
      second
      :last-market)

  ;; Compare each active player's profit in that completed round.
  (profit-summary (-> price-pressure :states second))

  ;; Compare immediate (tick 1) and adapted (tick 10) market outcomes.
  (scenario-report))
