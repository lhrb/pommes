(ns dev.lhrb.pommes.pommes-connection.regulars-simulation)

;; Regulars are deliberately a small, pure layer. The market simulation owns
;; dish choice and money; this namespace owns counts and probabilities.

(defn clamp [low high value]
  (max low (min high value)))

(defn research-progress
  "Diminishing progress in [0, 1). Level 25 is about 96% of the listed gain."
  [level]
  (- 1.0 (Math/exp (/ (- (double level)) 8.0))))

(defn research-modifier
  [research item effect topping]
  (reduce + 0.0
          (for [[item-id level] research
                :let [definition (get item item-id)]
                :when (and (= effect (:effect definition))
                           (or (nil? (:toppings definition))
                               (contains? (:toppings definition) topping)))]
            (* (research-progress level)
               (double (:maximum-modifier definition 0.0))))))

(defn regular-cap [population {:keys [maximum-population-share]}]
  (long (Math/floor (* (double population) (double maximum-population-share)))))

(defn satisfaction [offer]
  (clamp 0.0 1.0 (double (get-in offer [:components :preference-match] 0.0))))

(defn retained-count [count loss-rate]
  (long (Math/round (* (long (or count 0)) (- 1.0 (double loss-rate))))))

(defn return-count [count eat-rate satisfaction reliability]
  (long (Math/round (* (long (or count 0))
                       (double eat-rate)
                       (double satisfaction)
                       (+ 1.0 (double reliability))))))

(defn conversion-count [sales satisfaction conversion remaining-capacity]
  (long (min (max 0 remaining-capacity)
             (Math/round (* (long (or sales 0))
                            (double satisfaction)
                            (double conversion))))))

(defn prepare-regulars
  "Applies attrition and returns a player-local map ready for this tick.

  `offers` maps [player-id demographic-id] to the current best offer."
  [regular-rules research-items demographics players offers]
  (reduce (fn [result demographic]
            (let [demographic-id (:id demographic)
                  population (:population demographic)
                  cap (regular-cap population regular-rules)]
              (reduce (fn [result player]
                        (let [player-id (:id player)
                              retained (min cap (retained-count (get-in player [:regulars demographic-id] 0)
                                                                (:loss-rate regular-rules)))
                              offer (get offers [player-id demographic-id])
                              satisfaction (satisfaction offer)
                              reliability (research-modifier (:research player) research-items
                                                               :reliability (:topping (:dish offer)))
                              returns (min retained (return-count retained (:eat-rate demographic)
                                                                  satisfaction reliability))]
                          (assoc-in result [player-id demographic-id]
                                    {:count retained :returns returns :cap cap
                                     :satisfaction satisfaction})))
                      result players)))
          {} demographics))

(defn finalize-regulars
  "Adds newly converted normal customers after their ordinary-market sales are known.

  `normal-sales` maps [player-id demographic-id] to ordinary customer sales."
  [regular-rules research-items prepared players offers normal-sales]
  (into {}
        (for [player players]
          [(:id player)
           (into {}
                 (for [[demographic-id {:keys [count cap satisfaction]}]
                       (get prepared (:id player))
                       :let [offer (get offers [(:id player) demographic-id])
                             conversion (* (double (:base-conversion-chance regular-rules))
                                           (+ 1.0 (research-modifier (:research player) research-items :conversion
                                                                       (:topping (:dish offer)))))
                             added (conversion-count (get normal-sales [(:id player) demographic-id] 0)
                                                     satisfaction conversion (- cap count))]]
                   [demographic-id (+ count added)]))])))

(comment
  ;; Evaluate these in a REPL while tuning game-data values.
  (def rules {:base-conversion-chance 0.03 :loss-rate 0.001 :maximum-population-share 0.20})
  (def items {:potato-quality {:effect :conversion :maximum-modifier 0.30}})
  (def demographic {:id :students :population 500 :eat-rate 0.12})
  (def offer {:dish {:topping :ketchup} :components {:preference-match 0.8}})
  (def player {:id :ada :regulars {:students 50} :research {:potato-quality 10}})
  (def prepared (prepare-regulars rules items [demographic] [player] {[:ada :students] offer}))
  prepared
  (finalize-regulars rules items prepared [player] {[:ada :students] offer} {[:ada :students] 100})
  (research-progress 1)
  (research-progress 25))
