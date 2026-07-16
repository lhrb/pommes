(ns dev.lhrb.pommes.pommes-connection.demographics-simulation)

(def default-city-rules
  {:initial-population 100
   :maximum-population 1000000
   :population-growth-half-life-ticks 1440
   :demographics-update-every-ticks 60
   :composition-drift-start-population 937500
   :minimum-demographic-share 0.05
   :maximum-demographic-share 0.40
   :maximum-share-change 0.01})

(defn half-life-rate
  [half-life-ticks]
  (- 1.0
     (Math/pow 0.5
               (/ 1.0
                  (double half-life-ticks)))))

(defn- city-rule
  [city key]
  (get city key (get default-city-rules key)))

(defn- allocate-population
  [population shares]
  (let [raw (mapv #(* (double population) %) shares)
        whole (mapv #(long (Math/floor %)) raw)
        missing (- population (reduce + whole))
        ranked-indices (->> raw
                            (map-indexed (fn [index value]
                                           [index (- value (Math/floor value))]))
                            (sort-by (fn [[index remainder]] [(- remainder) index]))
                            (map first))]
    (reduce (fn [allocation index]
              (update allocation index inc))
            whole
            (take missing ranked-indices))))

(defn- demographic-shares
  [demographics]
  (let [total (reduce + 0 (map :population demographics))]
    (if (zero? total)
      (repeat (count demographics) (/ 1.0 (count demographics)))
      (mapv #(/ (double (:population %)) total) demographics))))

(defn- with-population
  [city population shares]
  (let [allocations (allocate-population population shares)]
    (-> city
        (assoc :population population)
        (assoc :demographics
               (mapv #(assoc %1 :population %2)
                     (:demographics city)
                     allocations)))))

(defn initial-city
  "Creates a game-local city from configured demographic weights."
  ([city]
   (initial-city city 1))
  ([city random-state]
   (let [city (merge default-city-rules city)
         initial-population (:initial-population city)
         shares (demographic-shares (:demographics city))]
     (-> city
         (with-population initial-population shares)
         (assoc :random-state (long random-state)
                :last-demographics-tick 0
                :next-demographics-at-tick
                (:demographics-update-every-ticks city))))))

(defn next-population
  "Moves population toward maximum-population over elapsed ticks."
  [city elapsed-ticks]
  (let [population (:population city)
        maximum (city-rule city :maximum-population)
        rate (half-life-rate (city-rule city :population-growth-half-life-ticks))
        remaining (* (- maximum population)
                     (Math/pow (- 1.0 rate) elapsed-ticks))]
    (long (Math/round (- maximum remaining)))))

(defn- next-random
  [random-state]
  (let [state (bit-and 0xffffffff
                       (unchecked-add
                        (unchecked-multiply 1664525 (long random-state))
                        1013904223))]
    [state (/ (double state) 4294967296.0)]))

(defn- random-index
  [random-state count]
  (let [[next-state value] (next-random random-state)]
    [next-state (min (dec count) (long (Math/floor (* value count))))]))

(defn- drift-shares
  [city shares]
  (let [minimum (city-rule city :minimum-demographic-share)
        maximum (city-rule city :maximum-demographic-share)
        maximum-change (city-rule city :maximum-share-change)
        source-candidates (keep-indexed #(when (> %2 minimum) %1) shares)
        random-state (:random-state city)]
    (if (empty? source-candidates)
      [random-state shares]
      (let [[random-state source-position]
            (random-index random-state (count source-candidates))
            source (nth source-candidates source-position)
            destination-candidates
            (keep-indexed #(when (and (not= %1 source) (< %2 maximum)) %1) shares)]
        (if (empty? destination-candidates)
          [random-state shares]
          (let [[random-state destination-position]
                (random-index random-state (count destination-candidates))
                destination (nth destination-candidates destination-position)
                transfer (min maximum-change
                              (- (nth shares source) minimum)
                              (- maximum (nth shares destination)))]
            [random-state
             (-> shares
                 (update source - transfer)
                 (update destination + transfer))]))))))

(defn- advance-once
  [city update-tick]
  (let [elapsed-ticks (- update-tick (:last-demographics-tick city))
        population (next-population city elapsed-ticks)
        shares (demographic-shares (:demographics city))
        [random-state shares]
        (if (>= population (city-rule city :composition-drift-start-population))
          (drift-shares city shares)
          [(:random-state city) shares])]
    (-> (with-population city population shares)
        (assoc :random-state random-state
               :last-demographics-tick update-tick
               :next-demographics-at-tick
               (+ update-tick (city-rule city :demographics-update-every-ticks))))))

(defn evolve-city
  "Advances a city through every demographic update due at tick."
  [city tick]
  (loop [city city]
    (if (<= (:next-demographics-at-tick city) tick)
      (recur (advance-once city (:next-demographics-at-tick city)))
      city)))

(comment
 (require '[dev.lhrb.pommes.pommes-connection.game-data :as game-data])

  ;; Start a city and inspect its growth over one seven-day game.
 (def city (initial-city (:city game-data/game-data) 42))
 (let [city (initial-city (:city game-data/game-data) 43)]
  (->> (range 0 20161 720)
       (map (fn [tick]
                (let [city (evolve-city city tick)]
                  {:tick tick
                   :population (:population city)
                   :shares (into {}
                                 (map (juxt :id
                                            #(double (/ (:population %)
                                                        (:population city))))
                                      (:demographics city)))})))))

  ;; The two-day point is about 937,506 inhabitants.
 (:population (evolve-city city 5760)))
