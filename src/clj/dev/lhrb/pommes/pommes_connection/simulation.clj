(ns dev.lhrb.pommes.pommes-connection.simulation)

(def costs
  {:size      {:small 0.55, :large 0.85}
   :seasoning {:salt 0.03, :pommesgewuerz 0.09}
   :topping   {:none 0.00
               :ketchup 0.12
               :mayo 0.16
               :ketchup-mayo 0.25
               :special 0.55
               :sate 0.60}})

(defn clamp [low high x]
  (max low (min high x)))

(defn closeness [actual ideal tolerance]
  (let [distance (Math/abs (- (double actual) (double ideal)))
        tolerance (max 0.0001 (double tolerance))]
    (clamp 0.0 1.0 (- 1.0 (/ distance tolerance)))))

(defn price-score [price max-price]
  ;; 1.0 is excellent, 0.0 is at the demographic's maximum,
  ;; and a price above that maximum becomes a negative score.
  (clamp -1.0 1.0
         (- 1.0 (/ (double price)
                   (max 0.01 (double max-price))))))

(defn dish-cost [{:keys [pommes topping]}]
  (+ (* (get-in costs [:size (:size pommes)] 0.0)
        (double (:portion pommes)))
     (get-in costs [:seasoning (:seasoning pommes)] 0.0)
     (get-in costs [:topping topping] 0.0)))

(defn dish-margin [dish]
  (- (double (:price dish)) (dish-cost dish)))

(defn weighted-average [weights scores]
  (let [total-weight (reduce + 0.0 (vals weights))]
    (if (zero? total-weight)
      0.0
      (/ (reduce-kv
           (fn [sum feature weight]
             (+ sum (* (double weight)
                       (double (get scores feature 0.0)))))
           0.0
           weights)
         total-weight))))

(defn feature-scores [demographic player dish]
  (let [preferences (:preferences demographic)
        pommes (:pommes dish)]
    {:size
     (get-in preferences [:size (:size pommes)] 0.0)

     :portion
     (closeness (:portion pommes)
                (get-in preferences [:portion :ideal] 1.0)
                (get-in preferences [:portion :tolerance] 0.5))

     :seasoning
     (get-in preferences [:seasoning (:seasoning pommes)] 0.0)

     :topping
     (get-in preferences [:topping (:topping dish)] 0.0)

     :price
     (price-score (:price dish)
                  (get-in preferences [:price :max] 10.0))

     :interior
     (get-in preferences [:interior (:interior player)] 0.0)}))

(defn score-dish [demographic player dish]
  (weighted-average
    (get-in demographic [:preferences :weights])
    (feature-scores demographic player dish)))

(defn effect-applies? [effect demographic]
  (or (nil? (:demographics effect))
      (contains? (:demographics effect) (:id demographic))))

(defn player-modifier [player demographic]
  (+ (double (get-in player [:market-modifiers :global] 0.0))
     (double (get-in player
                     [:market-modifiers :demographics (:id demographic)]
                     0.0))
     (reduce + 0.0
             (for [effect (:effects player)
                   :when (effect-applies? effect demographic)]
               (double (:utility effect 0.0))))))

(defn best-offer [demographic player]
  (let [scored
        (mapv (fn [dish]
                {:player-id (:id player)
                 :dish-id (:id dish)
                 :dish dish
                 :utility (+ (score-dish demographic player dish)
                             (player-modifier player demographic))})
              (:dishes player))]
    (when (empty? scored)
      (throw (ex-info "A player must offer at least one dish"
                      {:player-id (:id player)})))
    (apply max-key :utility scored)))

(defn with-softmax-shares [beta options]
  ;; Subtracting the maximum keeps exp numerically stable.
  (let [maximum (apply max (map :utility options))
        weighted (mapv
                   (fn [option]
                     (assoc option
                            :choice-weight
                            (Math/exp (* (double beta)
                                         (- (:utility option) maximum)))))
                   options)
        total (reduce + 0.0 (map :choice-weight weighted))]
    (mapv (fn [option]
            (assoc option :share (/ (:choice-weight option) total)))
          weighted)))

(defn simulate-demographic [players beta demographic]
  (let [demand (* (double (:population demographic))
                  (double (:eat-rate demographic)))
        choices (->> players
                     (mapv #(best-offer demographic %))
                     (with-softmax-shares beta))
        allocations
        (mapv
          (fn [{:keys [player-id dish-id dish share utility]}]
            (let [sales (* demand share)]
              {:player-id player-id
               :dish-id dish-id
               :utility utility
               :share share
               :sales sales
               :revenue (* sales (:price dish))
               :profit (* sales (dish-margin dish))}))
          choices)]
    {:demographic-id (:id demographic)
     :population (:population demographic)
     :demand demand
     :allocations allocations}))

(def empty-stats
  {:sales 0.0
   :revenue 0.0
   :profit 0.0
   :dish-sales {}})

(defn initial-stats [players]
  (into {}
        (map (fn [player]
               [(:id player) empty-stats])
             players)))

(defn add-allocation [stats allocation]
  (let [{:keys [player-id dish-id sales revenue profit]} allocation]
    (-> stats
        (update-in [player-id :sales] + sales)
        (update-in [player-id :revenue] + revenue)
        (update-in [player-id :profit] + profit)
        (update-in [player-id :dish-sales dish-id] (fnil + 0.0) sales))))

(defn tick-effects [effects]
  (->> effects
       (map #(update % :rounds-left dec))
       (filter #(pos? (:rounds-left %)))
       vec))

(defn simulate-round [{:keys [round city players rules] :as state}]
  (let [beta (double (get rules :choice-beta 3.0))
        market (mapv #(simulate-demographic players beta %)
                     (:demographics city))
        allocations (mapcat :allocations market)
        stats (reduce add-allocation
                      (initial-stats players)
                      allocations)
        updated-players
        (mapv
          (fn [player]
            (let [round-stats (get stats (:id player))]
              (-> player
                  (update :money (fnil + 0.0) (:profit round-stats))
                  (update :prestige (fnil + 0.0) (:sales round-stats))
                  (assoc :last-round round-stats)
                  (update :effects tick-effects))))
          players)]
    (assoc state
           :round (inc (or round 0))
           :players updated-players
           :market market)))

(def sample-state
  {:round 0
   :rules {:choice-beta 3.0}
   :city
   {:demographics
    [{:id :students
      :population 500
      :eat-rate 0.55
      :preferences
      {:weights {:size 0.4, :portion 0.6, :seasoning 0.3,
                 :topping 0.8, :price 1.8, :interior 0.2}
       :size {:small 0.9, :large 0.5}
       :portion {:ideal 0.9, :tolerance 0.5}
       :seasoning {:salt 0.8, :pommesgewuerz 0.7}
       :topping {:none 0.3, :ketchup 1.0, :mayo 0.7,
                 :ketchup-mayo 0.9, :special 0.4, :sate 0.5}
       :price {:max 5.0}
       :interior {:classic 0.5, :modern 0.8, :punk 1.0}}}

     {:id :families
      :population 350
      :eat-rate 0.35
      :preferences
      {:weights {:size 0.5, :portion 0.8, :seasoning 0.4,
                 :topping 0.7, :price 1.0, :interior 0.8}
       :size {:small 0.7, :large 0.9}
       :portion {:ideal 1.1, :tolerance 0.5}
       :seasoning {:salt 1.0, :pommesgewuerz 0.6}
       :topping {:none 0.5, :ketchup 1.0, :mayo 0.8,
                 :ketchup-mayo 0.9, :special 0.5, :sate 0.2}
       :price {:max 6.5}
       :interior {:classic 1.0, :modern 0.8, :punk 0.2}}}]}

   :players
   [{:id :fritza
     :money 20.0
     :prestige 0.0
     :interior :modern
     :market-modifiers {}
     :effects []
     :dishes
     [{:id :cheap-ketchup
       :pommes {:size :small, :portion 0.9, :seasoning :salt}
       :topping :ketchup
       :price 3.5}
      {:id :large-mayo
       :pommes {:size :large, :portion 1.15, :seasoning :pommesgewuerz}
       :topping :mayo
       :price 5.2}
      {:id :special
       :pommes {:size :large, :portion 1.0, :seasoning :pommesgewuerz}
       :topping :special
       :price 6.0}]}

    {:id :goldene-gabel
     :money 20.0
     :prestige 0.0
     :interior :classic
     :market-modifiers {}
     :effects [{:kind :rats
                :utility -0.20
                :rounds-left 1}]
     :dishes
     [{:id :classic
       :pommes {:size :large, :portion 1.0, :seasoning :salt}
       :topping :ketchup-mayo
       :price 4.8}
      {:id :kids
       :pommes {:size :small, :portion 0.8, :seasoning :salt}
       :topping :ketchup
       :price 3.8}
      {:id :sate
       :pommes {:size :large, :portion 1.1, :seasoning :pommesgewuerz}
       :topping :sate
       :price 6.5}]}]})

(comment
  (def next-state (simulate-round sample-state))
  (mapv #(select-keys % [:id :money :prestige :last-round])
        (:players next-state)))
