(ns dev.lhrb.pommes.pommes-connection.simulation
  (:require
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]))

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
  [catalog {:keys [pommes topping]}]
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
               0.0))))

(defn dish-margin
  [catalog dish]
  (- (double (:price dish))
     (dish-cost catalog dish)))

;; ---------------------------------------------------------------------------
;; Base demographic preferences
;; ---------------------------------------------------------------------------

(defn price-score
  "Scores the absolute price for a demographic.

   :cheap
   Lower is better. :max is the point where the score becomes zero.

   :value
   Prefers a price near :ideal.

   :status
   Also prefers a price near :ideal. Being expensive compared with competitors
   is handled separately by status-utility."
  [demographic dish]
  (let [price-preferences
        (get-in demographic
                [:preferences :price])

        model
        (:model price-preferences)

        price
        (double (:price dish))]

    (case model
      :cheap
      (let [max-price
            (max 0.01
                 (double
                   (get price-preferences
                        :max
                        5.0)))]

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
          (get price-preferences
               :tolerance
               2.0)))

      :status
      (ideal-score
        price
        (double
          (get price-preferences
               :ideal
               8.0))
        (double
          (get price-preferences
               :tolerance
               3.0)))

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
     (price-score demographic dish)

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

(defn effect-applies?
  [effect demographic]
  (or (nil? (:demographics effect))
      (contains?
        (:demographics effect)
        (:id demographic))))

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

     (reduce
       + 0.0
       (for [effect (:effects player)
             :when (effect-applies?
                     effect
                     demographic)]
         (double
           (get effect
                :utility
                0.0))))))

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

        total
        (+ preference
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
              :utility (:total components)})))
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
  [demand choices]
  (let [ranked
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
                                  (sort-by (juxt (comp - :remainder) :index)
                                           ranked)))]
    (mapv (fn [{:keys [index choice sales]}]
            [choice (+ sales (if (contains? bonus-indexes index) 1 0))])
          ranked)))

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
          :unit-cost (dish-cost catalog dish)
          :unit-margin (dish-margin catalog dish)
          :revenue (* sales
                      (double (:price dish)))
          :profit (* sales
                     (dish-margin catalog dish))))

      (assoc
        base
        :revenue 0.0
        :profit 0.0))))

(defn simulate-demographic
  [rules catalog market players demographic]
  (let [potential-demand
        (long
          (Math/round
            (* (double (:population demographic))
               (double (:eat-rate demographic)))))

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

        allocations
        (mapv
          (fn [[choice sales]]
            (allocate-choice catalog demographic choice sales))
          (allocate-orders potential-demand choices))

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

     :allocations allocations}))

;; ---------------------------------------------------------------------------
;; Player results
;; ---------------------------------------------------------------------------

(def empty-stats
  {:sales 0
   :revenue 0.0
   :profit 0.0
   :dish-sales {}
   :demographic-sales {}})

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
           profit]}]

  (if (= kind :outside)
    stats
    (-> stats
        (update-in
          [player-id :sales]
          (fnil + 0)
          sales)

        (update-in
          [player-id :revenue]
          (fnil + 0.0)
          revenue)

        (update-in
          [player-id :profit]
          (fnil + 0.0)
          profit)

        (update-in
          [player-id :dish-sales dish-id]
          (fnil + 0)
          sales)

        (update-in
          [player-id
          :demographic-sales
           demographic-id]
          (fnil + 0)
          sales))))

(defn update-player
  [player stats]
  (-> player
      ;; Money changes by profit, not revenue.
      (update
        :money
        (fnil + 0.0)
        (:profit stats))

      ;; Every sold dish produces one prestige.
      (update
        :prestige
        (fnil + 0)
        (:sales stats))

      (assoc
        :last-tick
        stats)

      (update
        :effects
        tick-effects)))

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
           players
           rules
           market]
    :as state}]

  (let [demographics
        (:demographics city)

        ;; The complete tick uses one immutable snapshot of all player offers.
        results
        (mapv
          #(simulate-demographic
             rules catalog
             (or market {})
             players
             %)
          demographics)

        allocations
        (mapcat :allocations
                results)

        stats
        (reduce
          add-allocation
          (initial-stats players)
          allocations)

        updated-players
        (mapv
          (fn [player]
            (update-player
              player
              (get stats
                   (:id player)
                   empty-stats)))
          players)

        updated-market
        (update-market
          rules
          (or market {})
          demographics
          results)]

    (assoc
      state
      :tick (inc (or tick 0))
      :players updated-players
      :market updated-market
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

(comment
(def sample-state
  {:tick 0

   :rules
   {:tick-seconds 30

    ;; Higher values make customers more aggressively choose the best offer.
    :choice-beta 3.5

    ;; 0.10 means market memory changes by 10% toward the latest tick.
    :market-memory-alpha 0.10

    :relative-price-cap 0.25
    :status-cap 0.25

    :budget-price-ratio 0.80
    :premium-price-ratio 1.40}

   ;; The market starts with assumptions about normal prices.
   ;; Recent topping and positioning shares default to uniform distributions.
   :market
   {:price-anchors
    {:students 4.50
     :families 5.50
     :yuppies 6.00}}

   :city
   {:demographics
    [{:id :students
      :population 500

      ;; Roughly 12% of this group considers buying fries each tick.
      :eat-rate 0.12

      ;; Buying nothing participates in softmax like another competitor.
      :outside-option-utility 0.48

      :behavior
      {:topping-variety-strength 0.20
       :positioning-variety-strength 0.10}

      :preferences
      {:weights
       {:size 1.0
        :seasoning 0.3
        :topping 0.9
        :price 1.8
        :interior 0.2}

       :size
       {:small 0.9
        :medium 0.7
        :large 0.5
        :extra-large 0.3}

       :seasoning
       {:salt 0.8
        :pommesgewuerz 0.7}

       :topping
       {:none 0.3
        :ketchup 1.0
        :mayo 0.7
        :ketchup-mayo 0.9
        :special 0.4
        :sate 0.5}

       :price
       {:model :cheap
        :max 5.00
        :anchor 4.50
        :relative-price-strength 0.25}

       :interior
       {:classic 0.5
        :modern 0.8
        :punk 1.0}}}

     {:id :families
      :population 350
      :eat-rate 0.08
      :outside-option-utility 0.50

      :behavior
      {:topping-variety-strength 0.05
       :positioning-variety-strength 0.03}

      :preferences
      {:weights
       {:size 1.3
        :seasoning 0.4
        :topping 0.7
        :price 1.0
        :interior 0.8}

       :size
       {:small 0.7
        :medium 0.8
        :large 0.9
        :extra-large 1.0}

       :seasoning
       {:salt 1.0
        :pommesgewuerz 0.6}

       :topping
       {:none 0.5
        :ketchup 1.0
        :mayo 0.8
        :ketchup-mayo 0.9
        :special 0.5
        :sate 0.2}

       :price
       {:model :value
        :ideal 5.50
        :tolerance 2.25
        :anchor 5.50
        :relative-price-strength 0.10}

       :interior
       {:classic 1.0
        :modern 0.8
        :punk 0.2}}}

     {:id :yuppies
      :population 180
      :eat-rate 0.07
      :outside-option-utility 0.52

      ;; This group likes novelty and exclusivity.
      :behavior
      {:topping-variety-strength 0.35
       :positioning-variety-strength 0.30}

      :preferences
      {:weights
       {:size 1.3
        :seasoning 0.5
        :topping 1.4
        :price 1.0
        :interior 1.3}

       ;; Smaller servings can be a feature rather than a weakness.
       :size
       {:small 1.0
        :medium 0.65
        :large 0.3
        :extra-large 0.0}

       :seasoning
       {:salt 0.4
        :pommesgewuerz 1.0}

       :topping
       {:none 0.2
        :ketchup 0.1
        :mayo 0.2
        :ketchup-mayo 0.1
        :special 1.0
        :sate 0.8}

       :price
       {:model :status

        ;; €9 is not considered a mistake for this demographic.
        :ideal 9.00
        :tolerance 3.00

        ;; Current normal market price.
        :anchor 6.00

        ;; Expensiveness relative to competitors can add utility.
        :status-strength 0.40}

       :interior
       {:classic 0.2
        :modern 1.0
        :punk 0.6}}}]}

   :players
   [{:id :fritza
     :money 20.0
     :prestige 0.0
     :interior :punk
     :market-modifiers {}
     :effects []

     :dishes
     [{:id :cheap-ketchup
       :pommes
       {:size :small
        :seasoning :salt}
       :topping :ketchup
       :price 3.50}

      {:id :large-mayo
       :pommes
       {:size :large
        :seasoning :pommesgewuerz}
       :topping :mayo
       :price 5.20}

      {:id :special
       :pommes
       {:size :large
        :seasoning :pommesgewuerz}
       :topping :special
       :price 6.00}]}

    {:id :goldene-gabel
     :money 20.0
     :prestige 0.0
     :interior :classic
     :market-modifiers {}

     ;; This penalty applies during the next calculated tick.
     :effects
     [{:kind :rats
       :utility -0.20
       :ticks-left 1}]

     :dishes
     [{:id :classic
       :pommes
       {:size :large
        :seasoning :salt}
       :topping :ketchup-mayo
       :price 4.80}

      {:id :kids
       :pommes
       {:size :small
        :seasoning :salt}
       :topping :ketchup
       :price 3.80}

      {:id :sate
       :pommes
       {:size :large
        :seasoning :pommesgewuerz}
       :topping :sate
       :price 6.50}]}

    {:id :pommes-lab
     :money 20.0
     :prestige 0.0
     :interior :modern
     :market-modifiers {}
     :effects []

     :dishes
     [{:id :micro-special
       :pommes
       {:size :small
        :seasoning :pommesgewuerz}
       :topping :special
       :price 9.00}

      {:id :sate-edition
       :pommes
       {:size :small
        :seasoning :pommesgewuerz}
       :topping :sate
       :price 8.50}

      {:id :normal-ketchup
       :pommes
       {:size :small
        :seasoning :salt}
       :topping :ketchup
       :price 4.50}]}]}))

(def sample-state
  (merge
   {:tick 0
    :players
    [{:id :fritza :money 20.0 :prestige 0.0 :interior :punk :market-modifiers {} :effects []
      :dishes [{:id :cheap-ketchup :pommes {:size :small :seasoning :salt} :topping :ketchup :price 3.50}
               {:id :large-mayo :pommes {:size :large :seasoning :pommesgewuerz} :topping :mayo :price 5.20}
               {:id :special :pommes {:size :large :seasoning :pommesgewuerz} :topping :special :price 6.00}]}
     {:id :goldene-gabel :money 20.0 :prestige 0.0 :interior :classic :market-modifiers {}
      :effects [{:kind :rats :utility -0.20 :ticks-left 1}]
      :dishes [{:id :classic :pommes {:size :large :seasoning :salt} :topping :ketchup-mayo :price 4.80}
               {:id :kids :pommes {:size :small :seasoning :salt} :topping :ketchup :price 3.80}
               {:id :sate :pommes {:size :large :seasoning :pommesgewuerz} :topping :sate :price 6.50}]}
     {:id :pommes-lab :money 20.0 :prestige 0.0 :interior :modern :market-modifiers {} :effects []
      :dishes [{:id :micro-special :pommes {:size :small :seasoning :pommesgewuerz} :topping :special :price 9.00}
               {:id :sate-edition :pommes {:size :small :seasoning :pommesgewuerz} :topping :sate :price 8.50}
               {:id :normal-ketchup :pommes {:size :small :seasoning :salt} :topping :ketchup :price 4.50}]}]}
   (game-data/simulation-config)))

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
          players)))))
