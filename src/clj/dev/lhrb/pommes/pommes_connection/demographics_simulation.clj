(ns dev.lhrb.pommes.pommes-connection.demographics-simulation)

(def default-city-rules
  {:population-anchors [[0 15000] [2880 30000] [11520 70000] [20160 100000]]
   :demographics-update-every-ticks 720
   :composition-drift-start-tick 2880
   :minimum-demographic-share 0.07
   :maximum-demographic-share 0.35
   :maximum-share-change 0.01
   :trend-transfer-share [0.08 0.12]
   :trend-completion-tolerance 0.001})

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

(defn- random-between
  [random-state minimum maximum]
  (let [[next-state value] (next-random random-state)]
    [next-state (+ minimum (* value (- maximum minimum)))]))

(defn- demographic-index
  [city demographic-id]
  (first (keep-indexed #(when (= demographic-id (:id %2)) %1)
                       (:demographics city))))

(defn- active-trend?
  [trend]
  (contains? trend :remaining-transfer-share))

(defn- select-trend
  [city shares]
  (let [minimum (city-rule city :minimum-demographic-share)
        maximum (city-rule city :maximum-demographic-share)
        [minimum-transfer maximum-transfer]
        (city-rule city :trend-transfer-share)
        [random-state transfer-share]
        (random-between (:random-state city) minimum-transfer maximum-transfer)
        previous-ids (into #{} (keep #(get (:demographic-trend city) %)
                                     [:growing-id :shrinking-id]))
        growing-candidates
        (keep-indexed #(when (and (not (contains? previous-ids (:id (nth (:demographics city) %1))))
                                  (<= %2 (- maximum transfer-share)))
                         %1)
                      shares)]
    (if (empty? growing-candidates)
      [random-state nil]
      (let [[random-state growing-position]
            (random-index random-state (count growing-candidates))
            growing (nth growing-candidates growing-position)
            shrinking-candidates
            (keep-indexed #(when (and (not= %1 growing)
                                       (not (contains? previous-ids (:id (nth (:demographics city) %1))))
                                       (>= %2 (+ minimum transfer-share)))
                            %1)
                          shares)]
        (if (empty? shrinking-candidates)
          [random-state nil]
          (let [[random-state shrinking-position]
                (random-index random-state (count shrinking-candidates))
                shrinking (nth shrinking-candidates shrinking-position)]
            [random-state
             {:growing-id (:id (nth (:demographics city) growing))
              :shrinking-id (:id (nth (:demographics city) shrinking))
              :transfer-share transfer-share
              :remaining-transfer-share transfer-share}]))))))

(defn- trend-complete?
  [city shares trend]
  (let [growing (demographic-index city (:growing-id trend))
        shrinking (demographic-index city (:shrinking-id trend))
        tolerance (city-rule city :trend-completion-tolerance)
        minimum (city-rule city :minimum-demographic-share)
        maximum (city-rule city :maximum-demographic-share)]
    (or (not (active-trend? trend))
        (nil? growing)
        (nil? shrinking)
        (<= (:remaining-transfer-share trend) tolerance)
        (<= (- (nth shares shrinking) minimum) tolerance)
        (<= (- maximum (nth shares growing)) tolerance))))

(defn- move-toward-trend
  [city shares trend]
  (let [growing (demographic-index city (:growing-id trend))
        shrinking (demographic-index city (:shrinking-id trend))
        minimum (city-rule city :minimum-demographic-share)
        maximum (city-rule city :maximum-demographic-share)
        maximum-change (city-rule city :maximum-share-change)
        transfer (min maximum-change
                      (:remaining-transfer-share trend)
                      (- (nth shares shrinking) minimum)
                      (- maximum (nth shares growing)))]
    (if (pos? transfer)
      [(-> shares
           (update shrinking - transfer)
           (update growing + transfer))
       (update trend :remaining-transfer-share - transfer)]
      [shares trend])))

(defn- drift-shares
  [city shares]
  (let [trend (:demographic-trend city)]
    (if (and trend (not (trend-complete? city shares trend)))
      (let [[shares trend] (move-toward-trend city shares trend)]
        [(:random-state city) shares trend])
      (let [[random-state trend] (select-trend city shares)]
        (if trend
          (let [[shares trend] (move-toward-trend city shares trend)]
            [random-state shares trend])
          [random-state shares nil])))))

(defn- advance-once
  [city update-tick]
  (let [population (next-population city update-tick)
        shares (demographic-shares (:demographics city))
        [random-state shares trend]
        (if (>= update-tick (city-rule city :composition-drift-start-tick))
          (drift-shares city shares)
          [(:random-state city) shares (:demographic-trend city)])]
    (-> (with-population city population shares)
        (assoc :random-state random-state
               :demographic-trend trend
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


 ;; Follow one seeded city through a seven-day game. Repeated growing and
 ;; shrinking IDs show how each market trend remains active across updates.
 (let [cities (reductions evolve-city
                         (initial-city (:city game-data/game-data) 42)
                         (range 0 20161 720))]
   (mapv (fn [city]
           {:tick (str (/ (:last-demographics-tick city) 120) "h")
            :population (:population city)
            :trend (select-keys (:demographic-trend city)
                                [:growing-id :shrinking-id
                                 :transfer-share :remaining-transfer-share])
            :shares (into {}
                          (map (fn [{:keys [id population]}]
                                 [id (format "%.1f%%"
                                             (* 100.0 (/ population (:population city))))])
                               (:demographics city)))})
         cities)))
