(ns dev.lhrb.pommes.pommes-connection.demographics-simulation)

(def default-city-rules
  {:population-anchors [[0 15000] [2880 30000] [11520 70000] [20160 100000]]
   :demographics-update-every-ticks 720
   :composition-drift-start-tick 2880
   :minimum-demographic-share 0.05
   :maximum-demographic-share 0.40
   :maximum-share-change 0.01})

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
         initial-population (second (first (:population-anchors city)))
         shares (demographic-shares (:demographics city))]
     (-> city
         (with-population initial-population shares)
        (assoc :random-state (long random-state)
                :demand-carry {}
                :last-demographics-tick 0
                :next-demographics-at-tick
                (:demographics-update-every-ticks city))))))

(defn- smoothstep [value]
  (* value value (- 3.0 (* 2.0 value))))

(defn next-population
  "Interpolates the configured population anchors at an absolute game tick."
  [city tick]
  (let [anchors (vec (city-rule city :population-anchors))
        [last-tick last-population] (last anchors)]
    (cond
      (<= tick (ffirst anchors)) (long (second (first anchors)))
      (>= tick last-tick) (long last-population)
      :else
      (let [[[from-tick from-population] [to-tick to-population]]
            (first (filter (fn [[[from] [to]]]
                             (<= from tick to))
                           (partition 2 1 anchors)))
            alpha (/ (- (double tick) from-tick) (- to-tick from-tick))
            eased (smoothstep alpha)]
        (long (Math/round (+ from-population
                             (* eased (- to-population from-population)))))))))

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
  (let [population (next-population city update-tick)
        shares (demographic-shares (:demographics city))
        [random-state shares]
        (if (>= update-tick (city-rule city :composition-drift-start-tick))
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
                                            #(int (* 100
                                                     (double
                                                      (/ (:population %)
                                                         (:population city))))))
                                      (:demographics city)))})))))

  ;; The city remains visibly smaller during the development phase.
 (:population (evolve-city city 5760)))
