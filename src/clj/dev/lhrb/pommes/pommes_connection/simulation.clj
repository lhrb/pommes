(ns dev.lhrb.pommes.pommes-connection.simulation
  (:require
   [dev.lhrb.pommes.pommes-connection.black-market :as black-market]
   [dev.lhrb.pommes.pommes-connection.demographics-simulation :as demographics-simulation]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.prestige :as prestige]
   [dev.lhrb.pommes.pommes-connection.regulars-simulation :as regulars]))

;; Each tick assigns a whole number of customers to offers. Softmax shares
;; still express preference, while largest-remainder allocation keeps the
;; result deterministic and reproducible.

;; ---------------------------------------------------------------------------
;; Numeric helpers
;; ---------------------------------------------------------------------------

(defn clamp
  [low high x]
  (max low (min high x)))

(defn square
  [x]
  (* x x))

(defn lerp
  [from to alpha]
  (+ (* (- 1.0 alpha) from)
     (* alpha to)))

(defn safe-div
  [numerator denominator]
  (if (zero? denominator)
    0.0
    (/ numerator denominator)))

(defn closeness
  "Linear score in [0, 1].

   At the ideal value the score is 1.
   At or beyond the tolerance distance the score is 0."
  [actual ideal tolerance]
  (let [distance
        (Math/abs
         (- (double actual)
            (double ideal)))

        tolerance
        (max 0.0001
             (double tolerance))]

    (clamp 0.0
           1.0
           (- 1.0
              (/ distance tolerance)))))

(defn ideal-score
  "Parabolic score in [-1, 1].

   At ideal:           1
   One tolerance away: 0
   Farther away:      -1"
  [value ideal tolerance]
  (let [tolerance
        (max 0.0001
             (double tolerance))

        distance
        (/ (- (double value)
              (double ideal))
           tolerance)]

    (clamp -1.0
           1.0
           (- 1.0
              (square distance)))))

(defn weighted-average
  [weights scores]
  (let [total-weight
        (reduce + 0.0
                (map double
                     (vals weights)))]

    (if (zero? total-weight)
      0.0
      (/ (reduce-kv
          (fn [sum feature weight]
            (+ sum
               (* (double weight)
                  (double
                   (get scores feature 0.0)))))
          0.0
          weights)
         total-weight))))

(defn uniform-shares
  [values]
  (let [values
        (vec values)

        share
        (safe-div 1.0
                  (count values))]

    (into {}
          (map (fn [value]
                 [value share]))
          values)))

;; ---------------------------------------------------------------------------
;; Dish economy
;; ---------------------------------------------------------------------------

(defn dish-cost
  ([catalog dish]
   (dish-cost catalog dish 1.0))
  ([catalog {:keys [pommes topping]} ingredient-cost-multiplier]
   (* (double ingredient-cost-multiplier)
      (+ (double
          (get-in catalog
                  [:sizes (:size pommes) :cost]
                  0.0))

         (double
          (get-in catalog
                  [:seasonings (:seasoning pommes) :cost]
                  0.0))

         (double
          (get-in catalog
                  [:toppings topping :cost]
                  0.0))))))

(defn dish-margin
  ([catalog dish]
   (dish-margin catalog dish 1.0))
  ([catalog dish ingredient-cost-multiplier]
   (- (double (:price dish))
      (dish-cost catalog dish ingredient-cost-multiplier))))

;; ---------------------------------------------------------------------------
;; Base demographic preferences
;; ---------------------------------------------------------------------------

(defn effect-applies?
  [effect demographic]
  (or (and (nil? (:demographic effect))
           (nil? (:demographics effect)))
      (= (:demographic effect) (:id demographic))
      (contains? (:demographics effect) (:id demographic))))

(defn effect-modifier
  [player type demographic]
  (reduce + 0.0
          (for [effect (:effects player)
                :when (and (= type (:type effect))
                           (effect-applies? effect demographic))]
            (double (:modifier effect 0.0)))))

(defn price-score
  "Scores the absolute price for a demographic.

   :cheap
   Lower is better. :max is the point where the score becomes zero.

   :value
   Prefers a price near :ideal.

   :status
   Also prefers a price near :ideal. Being expensive compared with competitors
   is handled separately by status-utility."
  [demographic player dish]
  (let [price-preferences
        (get-in demographic
                [:preferences :price])

        model
        (:model price-preferences)

        price
        (double (:price dish))

        tolerance-multiplier
        (+ 1.0 (effect-modifier player :price-tolerance demographic))]

    (case model
      :cheap
      (let [max-price
            (max 0.01
                 (* (double (get price-preferences :max 500))
                    tolerance-multiplier))]

        (clamp -1.0
               1.0
               (- 1.0
                  (/ price max-price))))

      :value
      (ideal-score
       price
       (double
        (get price-preferences
             :ideal
             5.0))
       (double
        (* tolerance-multiplier
           (double (get price-preferences :tolerance 200)))))

      :status
      (ideal-score
       price
       (double
        (get price-preferences
             :ideal
             8.0))
       (double
        (* tolerance-multiplier
           (double (get price-preferences :tolerance 300)))))

      0.0)))

(defn feature-scores
  [demographic player dish]
  (let [preferences
        (:preferences demographic)

        pommes
        (:pommes dish)]

    {:size
     (double
      (get-in preferences
              [:size (:size pommes)]
              0.0))

     :seasoning
     (double
      (get-in preferences
              [:seasoning (:seasoning pommes)]
              0.0))

     :topping
     (double
      (get-in preferences
              [:topping (:topping dish)]
              0.0))

     :price
     (price-score demographic player dish)

     :interior
     (double
      (get-in preferences
              [:interior (:interior player)]
              0.0))}))

(defn concept-match
  "Measures how well the non-price parts of the offer fit.

   The result gates the premium status bonus. A high price alone therefore
   cannot turn bad fries into a desirable premium product."
  [demographic feature-scores]
  (weighted-average
   (dissoc
    (get-in demographic
            [:preferences :weights]
            {})
    :price)
   feature-scores))

(defn preference-match
  [demographic feature-scores]
  (weighted-average
   (get-in demographic
           [:preferences :weights]
           {})
   feature-scores))

;; ---------------------------------------------------------------------------
;; Dynamic market context
;; ---------------------------------------------------------------------------

(defn default-price-anchor
  [demographic]
  (let [{:keys [anchor ideal max]}
        (get-in demographic
                [:preferences :price])]

    (double
     (or anchor
         ideal
         max
         5.0))))

(defn market-price-anchor
  [market demographic]
  (double
   (get-in market
           [:price-anchors (:id demographic)]
           (default-price-anchor demographic))))

(defn relative-price-utility
  "For non-status demographics, being cheaper than the market price anchor
   creates a utility bonus.

   This is intentionally capped so that one absurdly cheap offer does not
   completely destroy the market."
  [rules market demographic dish]
  (let [{:keys [model relative-price-strength]}
        (get-in demographic
                [:preferences :price])

        strength
        (double
         (or relative-price-strength
             0.0))

        price
        (max 0.01
             (double (:price dish)))

        anchor
        (max 0.01
             (market-price-anchor
              market
              demographic))

        ;; Positive when the dish is cheaper than the market.
        relative-price
        (- (Math/log
            (/ price anchor)))

        cap
        (double
         (get rules
              :relative-price-cap
              0.25))]

    (if (= model :status)
      0.0
      (clamp (- cap)
             cap
             (* strength
                relative-price)))))

(defn status-utility
  "Rewards a coherent premium offer for being expensive relative to the market.

   Absolute price fit still applies. A €100 dish will therefore fail because
   its absolute price score becomes terrible and customers can buy nothing."
  [rules market demographic dish concept]
  (let [{:keys [model status-strength]}
        (get-in demographic
                [:preferences :price])

        strength
        (double
         (or status-strength
             0.0))

        price
        (max 0.01
             (double (:price dish)))

        anchor
        (max 0.01
             (market-price-anchor
              market
              demographic))

        relative-premium
        (Math/log
         (/ price anchor))

        ;; Premium positioning only works when the non-price concept fits.
        gate
        (clamp 0.0
               1.0
               concept)

        cap
        (double
         (get rules
              :status-cap
              0.25))]

    (if (= model :status)
      (clamp (- cap)
             cap
             (* strength
                relative-premium
                gate))
      0.0)))

(defn positioning-category
  [rules market demographic dish]
  (let [anchor
        (max 0.01
             (market-price-anchor
              market
              demographic))

        ratio
        (/ (double (:price dish))
           anchor)

        budget-threshold
        (double
         (get rules
              :budget-price-ratio
              0.80))

        premium-threshold
        (double
         (get rules
              :premium-price-ratio
              1.40))]

    (cond
      (< ratio budget-threshold)
      :budget

      (> ratio premium-threshold)
      :premium

      :else
      :mainstream)))

(defn recent-share
  [market demographic-id dimension value default]
  (double
   (get-in market
           [:recent-shares
            demographic-id
            dimension
            value]
           default)))

(defn topping-trend-utility
  "Positive strength rewards underrepresented toppings.

   Negative strength produces conformist demographics that prefer whatever
   is already popular."
  [market demographic dish]
  (let [options
        (keys
         (get-in demographic
                 [:preferences :topping]
                 {}))

        target
        (safe-div 1.0
                  (count options))

        current
        (recent-share
         market
         (:id demographic)
         :topping
         (:topping dish)
         target)

        strength
        (double
         (get-in demographic
                 [:behavior
                  :topping-variety-strength]
                 0.0))]

    (* strength
       (- target current))))

(defn positioning-trend-utility
  "Rewards positioning categories that are currently underrepresented.

   This prevents all players from permanently converging on the same premium,
   mainstream, or budget strategy."
  [rules market demographic dish]
  (let [category
        (positioning-category
         rules
         market
         demographic
         dish)

        target
        (/ 1.0
           (count (:positioning-categories rules)))

        current
        (recent-share
         market
         (:id demographic)
         :positioning
         category
         target)

        strength
        (double
         (get-in demographic
                 [:behavior
                  :positioning-variety-strength]
                 0.0))]

    (* strength
       (- target current))))

;; ---------------------------------------------------------------------------
;; Temporary effects and sabotage
;; ---------------------------------------------------------------------------

(defn temporary-effect-utility
  [player demographic]
  (+ (double
      (get-in player
              [:market-modifiers :global]
              0.0))

     (double
      (get-in player
              [:market-modifiers
               :demographics
               (:id demographic)]
              0.0))

     (reduce + 0.0
             (for [effect (:effects player)
                   :when (effect-applies? effect demographic)]
               (case (:type effect)
                 :demographic-attraction (double (:modifier effect 0.0))
                 :demographic-repulsion (- (double (:modifier effect 0.0)))
                 :global-attraction (double (:modifier effect 0.0))
                 (double (:utility effect 0.0)))))))

(defn tick-effects
  [effects]
  (->> effects
       (map
        #(update %
                 :ticks-left
                 (fnil dec 1)))
       (filter
        #(pos?
          (:ticks-left %)))
       vec))

;; ---------------------------------------------------------------------------
;; Offer utility
;; ---------------------------------------------------------------------------

(defn offer-components
  [rules market demographic player dish]
  (let [features
        (feature-scores
         demographic
         player
         dish)

        concept
        (concept-match
         demographic
         features)

        preference
        (preference-match
         demographic
         features)

        relative-price
        (relative-price-utility
         rules
         market
         demographic
         dish)

        status
        (status-utility
         rules
         market
         demographic
         dish
         concept)

        topping-trend
        (topping-trend-utility
         market
         demographic
         dish)

        positioning-trend
        (positioning-trend-utility
         rules
         market
         demographic
         dish)

        effects
        (temporary-effect-utility
         player
         demographic)

        research-quality
        (regulars/research-modifier (:research player) (get-in game-data/game-data [:research :items])
                                    :concept-quality (:topping dish))

        total
        (+ preference research-quality
           relative-price
           status
           topping-trend
           positioning-trend
           effects)]

    {:features features
     :concept-match concept
     :preference-match preference
     :relative-price relative-price
     :status status
     :topping-trend topping-trend
     :positioning-trend positioning-trend
     :effects effects
     :research-quality research-quality
     :total total}))

(defn best-offer
  "Returns the player's best dish for one demographic.

   Offering three similar dishes therefore does not multiply market share."
  [rules market demographic player]
  (when (empty? (:dishes player))
    (throw
     (ex-info
      "A player must offer at least one dish"
      {:player-id (:id player)})))

  (->> (:dishes player)
       (map
        (fn [dish]
          (let [components
                (offer-components
                 rules
                 market
                 demographic
                 player
                 dish)]

            (let [research-items (get-in game-data/game-data [:research :items])
                  order-value (regulars/research-modifier (:research player) research-items
                                                          :order-value (:topping dish))
                  efficiency (regulars/research-modifier (:research player) research-items
                                                         :ingredient-efficiency (:topping dish))]
            {:kind :purchase
             :player-id (:id player)
             :dish-id (:id dish)
             :dish dish

             :positioning
             (positioning-category
              rules
              market
              demographic
              dish)

             :components components
             :order-value-multiplier
             (* (+ 1.0 order-value)
                (+ 1.0 (effect-modifier player :average-order-size demographic)))
             :ingredient-cost-multiplier (- 1.0 efficiency)
             :utility (:total components)}))))
       (apply max-key :utility)))

(defn outside-option
  "Represents customers deciding not to buy fries this tick."
  [demographic]
  (let [utility
        (double
         (get demographic
              :outside-option-utility
              0.45))]

    {:kind :outside
     :player-id nil
     :dish-id nil
     :dish nil
     :positioning nil
     :components {:total utility}
     :utility utility}))

;; ---------------------------------------------------------------------------
;; Softmax choice
;; ---------------------------------------------------------------------------

(defn with-softmax-shares
  [beta options]
  ;; Subtracting the highest utility prevents numeric overflow.
  (let [maximum
        (apply max
               (map :utility options))

        weighted
        (mapv
         (fn [option]
           (assoc
            option
            :choice-weight
            (Math/exp
             (* (double beta)
                (- (double (:utility option))
                   maximum)))))
         options)

        total-weight
        (reduce + 0.0
                (map :choice-weight
                     weighted))]

    (mapv
     (fn [option]
       (assoc
        option
        :share
        (safe-div
         (:choice-weight option)
         total-weight)))
     weighted)))

;; ---------------------------------------------------------------------------
;; Demographic simulation
;; ---------------------------------------------------------------------------

(defn allocate-orders
  "Allocates a whole customer count according to choice shares.

   Remaining orders go to the largest fractional remainders. Ties preserve
   the original choice order, keeping equivalent simulations reproducible."
  ([demand choices]
   (allocate-orders demand choices 0))
  ([demand choices tie-offset]
  (let [choice-count (count choices)
        ranked
        (mapv (fn [index choice]
                (let [expected (* demand (double (:share choice)))
                      sales (long (Math/floor expected))]
                  {:index index
                   :choice choice
                   :sales sales
                   :remainder (- expected sales)}))
              (range)
              choices)
        remaining (- demand (reduce + 0 (map :sales ranked)))
        bonus-indexes (into #{}
                            (map :index)
                            (take remaining
                                  (sort-by (juxt (comp - :remainder)
                                                 #(mod (- (:index %) tie-offset) choice-count))
                                           ranked)))]
    (mapv (fn [{:keys [index choice sales]}]
            [choice (+ sales (if (contains? bonus-indexes index) 1 0))])
          ranked))))

(defn- choice-key [choice]
  (if (= :outside (:kind choice))
    :outside
    [(:player-id choice) (:dish-id choice)]))

(defn allocate-orders-with-carry
  "Largest-remainder allocation with durable per-choice fractions.

   Small opening markets no longer give every tied order to the first player."
  [demand choices previous-carry tie-offset]
  (let [choice-count (count choices)
        ranked (mapv (fn [index choice]
                       (let [key (choice-key choice)
                             raw (* demand (double (:share choice)))
                             previous (double (get previous-carry key 0.0))
                             sales (long (Math/floor raw))]
                         {:index index :key key :choice choice :sales sales
                          :raw raw :previous previous
                          :priority (+ previous (- raw sales))}))
                     (range) choices)
        remaining (- demand (reduce + 0 (map :sales ranked)))
        bonuses (frequencies
                 (map :index
                      (take remaining
                            (cycle (sort-by (juxt (comp - :priority)
                                                 #(mod (- (:index %) tie-offset) choice-count))
                                            ranked)))))
        allocations (mapv (fn [{:keys [index choice sales]}]
                            [choice (+ sales (get bonuses index 0))])
                          ranked)
        next-carry (into {}
                         (map (fn [{:keys [key raw previous index sales]}]
                                [key (- (+ previous raw) sales (get bonuses index 0))]))
                         ranked)]
    [allocations next-carry]))

(defn allocate-choice
  [catalog demographic choice sales]
  (let [sales (long sales)

        base
        {:kind (:kind choice)
         :demographic-id (:id demographic)
         :player-id (:player-id choice)
         :dish-id (:dish-id choice)
         :utility (:utility choice)
         :components (:components choice)
         :share (:share choice)
         :source (:source choice :ordinary)
         :sales sales}]

    (if (= :purchase
           (:kind choice))

      (let [dish
            (:dish choice)]

        (assoc
         base
         :topping (:topping dish)
         :positioning (:positioning choice)
         :price (double (:price dish))
         :unit-cost (dish-cost catalog dish (:ingredient-cost-multiplier choice 1.0))
         :unit-margin (dish-margin catalog dish (:ingredient-cost-multiplier choice 1.0))
         :order-value-multiplier (:order-value-multiplier choice 1.0)
         :revenue (* sales
                     (double (:price dish))
                     (:order-value-multiplier choice 1.0))
         :profit (* sales
                    (dish-margin catalog dish (:ingredient-cost-multiplier choice 1.0))
                    (:order-value-multiplier choice 1.0))))

      (assoc
       base
       :revenue 0.0
       :profit 0.0))))

(defn simulate-demographic
  [rules catalog market players demographic]
  (let [potential-demand
        (long (or (:tick-demand demographic)
                  (Math/round (* (double (:population demographic))
                                 (double (:order-rate-per-tick demographic 0.0))
                                 (double (:demand-ticks rules 1))))))

        beta
        (double
         (get rules
              :choice-beta
              3.0))

        player-options
        (mapv
         #(best-offer
           rules
           market
           demographic
           %)
         players)

        options
        (conj player-options
              (outside-option demographic))

        choices
        (with-softmax-shares
          beta
          options)

        tie-offset
        (Math/floorMod
         (+ (long (:tick demographic 0)) (long (hash (:id demographic))))
         (count choices))

        [order-allocation order-carry]
        (allocate-orders-with-carry potential-demand choices
                                    (get-in market [:order-carry (:id demographic)] {})
                                    tie-offset)

        allocations
        (mapv
         (fn [[choice sales]]
           (allocate-choice catalog demographic choice sales))
         order-allocation)

        purchases
        (filter
         #(= :purchase
             (:kind %))
         allocations)

        outside
        (first
         (filter
          #(= :outside
              (:kind %))
          allocations))]

    {:demographic-id (:id demographic)
     :population (:population demographic)
     :potential-demand potential-demand

     :served-demand
     (reduce + 0
             (map :sales
                  purchases))

     :unserved-demand
     (:sales outside)

     :order-carry order-carry

     :allocations allocations}))

;; ---------------------------------------------------------------------------
;; Player results
;; ---------------------------------------------------------------------------

(def empty-stats
  {:sales 0
   :regular-sales 0
   :revenue 0
   :profit 0
   :dish-sales {}
   :demographic-sales {}})

(defn allocate-city-demand
  "Allocates deterministic whole orders at the game's per-tick appetite.

   `:demand-ticks` lets balance simulations aggregate multiple live ticks."
  [rules city]
  (let [demand-ticks (double (:demand-ticks rules 1))]
    (reduce (fn [[city demographics] demographic]
              (let [id (:id demographic)
                    order-rate (double (or (:order-rate-per-tick demographic)
                                           (:default-order-rate-per-tick rules)
                                           0.0))
                    raw (+ (* (double (:population demographic))
                              order-rate
                              demand-ticks)
                           (double (get-in city [:demand-carry id] 0.0)))
                    demand (long (Math/floor raw))]
                [(assoc-in city [:demand-carry id] (- raw demand))
                 (conj demographics (assoc demographic
                                           :order-rate-per-tick order-rate
                                           :tick-demand demand
                                           :tick (inc (long (:tick rules 0)))))]))
            [city []]
            (:demographics city))))

(defn- add-demographic-sales
  [game-stats demographic-sales]
  (update game-stats :demographic-sales
          (fn [totals]
            (merge-with + (or totals {}) demographic-sales))))

(defn initial-stats
  [players]
  (into {}
        (map
         (fn [player]
           [(:id player)
            empty-stats]))
        players))

(defn add-allocation
  [stats
   {:keys [kind
           player-id
           dish-id
           demographic-id
           sales
           revenue
           profit
           source]}]

  (if (= kind :outside)
    stats
    (cond-> (update-in stats
         [player-id :sales]
         (fnil + 0)
         sales)
      (= :regular source) (update-in [player-id :regular-sales] (fnil + 0) sales)
      :always (update-in
         [player-id :revenue]
         (fnil + 0.0)
         revenue)
      :always (update-in
         [player-id :profit]
         (fnil + 0.0)
         profit)
      :always (update-in
         [player-id :dish-sales dish-id]
         (fnil + 0)
         sales)
      :always (update-in
         [player-id
          :demographic-sales
          demographic-id]
         (fnil + 0)
         sales))))

(defn update-player
  [prestige-rules player stats]
  (let [audit-loss (if (:audited? stats) (long (:audit-penalty prestige-rules 0)) 0)]
    (-> player
      ;; Money changes by tax-adjusted profit, not revenue.
      (update
       :money
       #(max 0 (+ (long (or % 0)) (long (:profit stats)))))

      (update :prestige #(max 0 (- (long (or % 0)) audit-loss)))

      (assoc
       :last-tick
       stats)

      ;; Keep durable totals separate from the latest-tick receipt. The stats
      ;; page can use this without replaying every historical game tick.
      (update :game-stats add-demographic-sales (:demographic-sales stats))

      (update-in [:game-stats :score-window] prestige/add-window-stats stats)

      (cond-> (pos? audit-loss)
        (update-in [:game-stats :prestige-by-source :audits] (fnil - 0) audit-loss))

      (update
       :effects
       tick-effects))))

;; ---------------------------------------------------------------------------
;; Market memory
;; ---------------------------------------------------------------------------

(defn observed-share-map
  [values allocations value-key]
  (let [totals
        (reduce
         (fn [result allocation]
           (update
            result
            (get allocation value-key)
            (fnil + 0.0)
            (double (:sales allocation))))
         (zipmap values
                 (repeat 0.0))
         allocations)

        total-sales
        (reduce + 0.0
                (vals totals))]

    (when (pos? total-sales)
      (into {}
            (map
             (fn [[value sales]]
               [value
                (/ sales total-sales)]))
            totals))))

(defn observed-price
  [allocations]
  (let [total-sales
        (reduce + 0.0
                (map :sales
                     allocations))]

    (when (pos? total-sales)
      (/ (reduce
          + 0.0
          (map
           (fn [{:keys [price sales]}]
             (* (double price)
                (double sales)))
           allocations))
         total-sales))))

(defn smooth-share-map
  [previous observed alpha values]
  (let [default
        (uniform-shares values)

        previous
        (merge default
               previous)]

    (if observed
      (into {}
            (map
             (fn [value]
               [value
                (lerp
                 (double
                  (get previous
                       value
                       0.0))
                 (double
                  (get observed
                       value
                       0.0))
                 alpha)]))
            values)

      previous)))

(defn update-market-for-demographic
  [rules market demographic result]
  (let [demographic-id
        (:id demographic)

        alpha
        (double
         (get rules
              :market-memory-alpha
              0.10))

        purchases
        (filter
         #(= :purchase
             (:kind %))
         (:allocations result))

        toppings
        (keys
         (get-in demographic
                 [:preferences :topping]
                 {}))

        observed-toppings
        (observed-share-map
         toppings
         purchases
         :topping)

        observed-positioning
        (observed-share-map
         (:positioning-categories rules)
         purchases
         :positioning)

        observed-anchor
        (observed-price purchases)

        previous-anchor
        (market-price-anchor
         market
         demographic)]

    (-> market
        (assoc-in [:order-carry demographic-id] (:order-carry result))
        (assoc-in
         [:recent-shares
          demographic-id
          :topping]
         (smooth-share-map
          (get-in market
                  [:recent-shares
                   demographic-id
                   :topping]
                  {})
          observed-toppings
          alpha
          toppings))

        (assoc-in
         [:recent-shares
          demographic-id
          :positioning]
         (smooth-share-map
          (get-in market
                  [:recent-shares
                   demographic-id
                   :positioning]
                  {})
          observed-positioning
          alpha
          (:positioning-categories rules)))

        (assoc-in
         [:price-anchors
          demographic-id]
         (if observed-anchor
           (lerp
            previous-anchor
            observed-anchor
            alpha)
           previous-anchor)))))

(defn update-market
  [rules market demographics results]
  (let [result-by-id
        (into {}
              (map
               (juxt :demographic-id
                     identity))
              results)]

    (reduce
     (fn [current demographic]
       (update-market-for-demographic
        rules
        current
        demographic
        (get result-by-id
             (:id demographic))))
     (or market {})
     demographics)))

;; ---------------------------------------------------------------------------
;; Complete tick
;; ---------------------------------------------------------------------------

(defn simulate-tick
  [{:keys [tick
           city
           catalog
           black-market
           players
           rules
           market]
    :as state}]

  (let [city
        (if (:next-demographics-at-tick city)
          city
          (demographics-simulation/initial-city city))

        [city demographics]
        (allocate-city-demand (assoc rules :tick (or tick 0)) city)

        ;; The complete tick uses one immutable snapshot of all player offers.
        offers
        (into {}
              (for [demographic demographics
                    player players]
                [[(:id player) (:id demographic)]
                 (best-offer rules (or market {}) demographic player)]))

        prepared-regulars
        (regulars/prepare-regulars (assoc (:regulars state)
                                         :demand-ticks (:demand-ticks rules 1)
                                         :tick (inc (or tick 0)))
                                   (get-in state [:research :items])
                                   demographics players offers)

        available-demographics
        (mapv (fn [demographic]
                (let [assigned (reduce + 0
                                       (map (fn [player]
                                              (get-in prepared-regulars [(:id player) (:id demographic) :count] 0))
                                            players))]
                  (update demographic :population #(max 0 (- % assigned)))))
              demographics)

        results
        (mapv
         #(simulate-demographic
           rules catalog
           (or market {})
           players
           %)
         available-demographics)

        regular-allocations
        (keep identity
              (for [demographic demographics
                    player players]
                (let [returns (get-in prepared-regulars [(:id player) (:id demographic) :returns] 0)]
                  (when (pos? returns)
                    (let [offer (get offers [(:id player) (:id demographic)])]
                      (allocate-choice catalog demographic
                                       (-> offer
                                           (assoc :source :regular))
                                       returns))))))

        allocations
        (concat regular-allocations (mapcat :allocations results))

        normal-sales
        (reduce (fn [totals allocation]
                  (if (and (= :purchase (:kind allocation)) (= :ordinary (:source allocation)))
                    (update totals [(:player-id allocation) (:demographic-id allocation)] (fnil + 0) (:sales allocation))
                    totals))
                {} allocations)

        updated-regulars
        (regulars/finalize-regulars (assoc (:regulars state) :tick (inc (or tick 0)))
                                    (get-in state [:research :items])
                                    prepared-regulars players offers normal-sales)

        stats
        (reduce
         add-allocation
         (initial-stats players)
         allocations)

        updated-players
        (mapv
         (fn [player]
           (let [[taxed-player receipt]
                 (black-market/settle-player black-market (inc (or tick 0)) player
                                             (get stats (:id player) empty-stats))]
             (assoc (update-player (:prestige state) taxed-player receipt)
                    :regulars (get updated-regulars (:id player) {}))))
         players)

        next-tick (inc (or tick 0))

        updated-players
        (if (and (pos? (get-in state [:game :score-every-ticks] 0))
                 (zero? (mod next-tick (get-in state [:game :score-every-ticks]))))
          (prestige/apply-round updated-players
                                (prestige/score-round state updated-players demographics)
                                next-tick)
          updated-players)

        updated-market
        (update-market
         rules
         (or market {})
         demographics
         results)

        updated-city
        (demographics-simulation/evolve-city city next-tick)]

    (assoc
     state
     :tick next-tick
     :players updated-players
     :market updated-market
     :city updated-city
     :last-market results)))

(defn run-ticks
  [state tick-count]
  (nth
   (iterate simulate-tick
            state)
   tick-count))

;; ---------------------------------------------------------------------------
;; Sample game
;; ---------------------------------------------------------------------------

(def sample-state
  (merge
   {:tick 0
    :players
    [{:id :fritza :money 6000000 :prestige 0 :interior :street-food :market-modifiers {} :effects []
      :dishes [{:id :cheap-ketchup :pommes {:size :small :seasoning :salt} :topping :ketchup :price 350}
               {:id :large-mayo :pommes {:size :large :seasoning :pommesgewuerz} :topping :mayo :price 520}
               {:id :special :pommes {:size :large :seasoning :pommesgewuerz} :topping :special :price 600}]}
     {:id :goldene-gabel :money 6000000 :prestige 0 :interior :classic :market-modifiers {}
      :effects [{:kind :rats :utility -0.20 :ticks-left 1}]
      :dishes [{:id :classic :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 480}
               {:id :kids :pommes {:size :small :seasoning :salt} :topping :ketchup :price 380}
               {:id :sate :pommes {:size :large :seasoning :pommesgewuerz} :topping :sate :price 650}]}
     {:id :pommes-lab :money 6000000 :prestige 0 :interior :modern :market-modifiers {} :effects []
      :dishes [{:id :micro-special :pommes {:size :small :seasoning :pommesgewuerz} :topping :special :price 900}
               {:id :sate-edition :pommes {:size :small :seasoning :pommesgewuerz} :topping :sate :price 850}
               {:id :normal-ketchup :pommes {:size :small :seasoning :salt} :topping :ketchup :price 450}]}]}
   (game-data/simulation-config)))

;; Compare one simulated tick with and without an action. Effects are read
  ;; from game-data, so these examples follow future balance changes.
(defn example-state-with-action [state action-id target-player-id]
  (let [action (game-data/action action-id)]
    (update state :players
            (fn [players]
              (mapv (fn [player]
                      (if (= target-player-id (:id player))
                        (update player :effects into
                                (map #(assoc %
                                             :action-id action-id
                                             :ticks-left (:duration action))
                                     (:effects action)))
                        player))
                    players)))))

(defn example-player-result [state player-id]
  (let [player (some #(when (= player-id (:id %)) %) (:players state))]
    (select-keys (assoc (:last-tick player) :money (:money player))
                 [:money :sales :revenue :profit :demographic-sales])))

(defn late-city-state [state]
  (update state :city demographics-simulation/evolve-city 20160))

(defn compare-action-example [sample-state action-id target-player-id]
  (let [without-effect (simulate-tick sample-state)
        with-effect (simulate-tick
                     (example-state-with-action sample-state action-id target-player-id))]
    {:without-effect (example-player-result without-effect target-player-id)
     :with-effect (example-player-result with-effect target-player-id)}))

(comment
  ;; Simulate one 30-second tick.
  (def state-1
    (simulate-tick sample-state))

  ;; Inspect player results.
  (mapv
   #(select-keys
     %
     [:id
      :money
      :prestige
      :last-tick])
   (:players state-1))

  ;; Inspect why yuppies chose each offer.
  (->> (:last-market state-1)
       (filter
        #(= :yuppies
            (:demographic-id %)))
       first
       :allocations
       (mapv
        #(select-keys
          %
          [:kind
           :player-id
           :dish-id
           :utility
           :components
           :share
           :sales
           :profit])))

  ;; Five minutes of market adaptation.
  (def state-after-ten-ticks
    (run-ticks sample-state 10))

  ;; Example demographic-specific sabotage.
  (def sabotaged-state
    (update
     sample-state
     :players
     (fn [players]
       (mapv
        (fn [player]
          (if (= :pommes-lab
                 (:id player))
            (update
             player
             :effects
             conj
             {:kind :influencer-scandal
              :utility -0.30
              :demographics #{:yuppies}
              :ticks-left 3})
            player))
        players))))

  (def s (million-city-state sample-state))

  (:city s)

  ;; Send Punks affects a rival. Compare Pommes Lab's student/family/retiree sales.
  (def send-punks-example
    (compare-action-example s :send-punks :pommes-lab))

  ;; Rocker Meeting attracts students but makes each successful order 20% larger.
  (def rocker-meeting-example
    (compare-action-example s :rocker-meeting :fritza))

  ;; Influencer Visit attracts yuppies and foodies while increasing price tolerance.
  (def influencer-visit-example
    (compare-action-example s :influencer-visit :pommes-lab)))
