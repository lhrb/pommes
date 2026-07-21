(ns dev.lhrb.pommes.pommes-connection.prestige)

(def empty-window
  {:sales 0
   :regular-sales 0
   :profit 0
   :dish-sales {}
   :demographic-sales {}})

(defn add-window-stats
  [window tick-stats]
  (-> (merge empty-window window)
      (update :sales + (long (:sales tick-stats 0)))
      (update :regular-sales + (long (:regular-sales tick-stats 0)))
      (update :profit + (long (:profit tick-stats 0)))
      (update :dish-sales #(merge-with + (or % {}) (:dish-sales tick-stats {})))
      (update :demographic-sales #(merge-with + (or % {}) (:demographic-sales tick-stats {})))))

(defn- split-pool
  [entries value-fn pool positive?]
  (let [best (when (seq entries) (apply max (map value-fn entries)))
        winners (when (and best (or (not positive?) (pos? best)))
                  (filterv #(= best (value-fn %)) entries))
        points (if (seq winners) (quot (long pool) (count winners)) 0)]
    (into {} (map (fn [entry] [(:id entry) points]) winners))))

(defn- proportional-pool
  [entries value-fn pool]
  (let [pool (long pool)
        total (reduce + 0 (map value-fn entries))]
    (if (pos? total)
      (let [shares (mapv (fn [entry]
                           (let [quota (* (double pool) (/ (double (value-fn entry)) total))]
                             {:id (:id entry)
                              :points (long (Math/floor quota))
                              :remainder (- quota (Math/floor quota))}))
                         entries)
            remaining (- pool (reduce + 0 (map :points shares)))]
        (loop [groups (->> shares (group-by :remainder) (sort-by key >))
               remaining remaining
               awards (into {} (map (juxt :id :points) shares))]
          (if (or (zero? remaining) (empty? groups))
            awards
            (let [[_ tied] (first groups)]
              (if (<= (count tied) remaining)
                (recur (rest groups) (- remaining (count tied))
                       (reduce #(update %1 (:id %2) inc) awards tied))
                awards)))))
      {})))

(defn- ranked-points
  [players value-fn points]
  (let [groups (->> players
                    (group-by value-fn)
                    (filter (comp pos? key))
                    (sort-by key >))]
    (loop [groups groups rank 0 result {}]
      (if (or (empty? groups) (>= rank (count points)))
        result
        (let [[_ tied] (first groups)
              occupied (count tied)
              pool (reduce + 0 (take occupied (drop rank points)))
              share (quot pool occupied)]
          (recur (rest groups) (+ rank occupied)
                 (reduce #(assoc %1 (:id %2) share) result tied)))))))

(defn- add-award
  [awards source points]
  (reduce-kv (fn [result player-id value]
               (if (zero? value)
                 result
                 (update-in result [player-id source] (fnil + 0) value)))
             awards points))

(defn score-round
  "Returns prestige awards by player and source for one completed score window."
  [config players demographics]
  (let [rules (:prestige config)
        window #(get-in % [:game-stats :score-window] empty-window)
        market-awards
        (reduce (fn [awards demographic]
                  (add-award awards :demographic-market-share
                             (proportional-pool players
                                                #(get-in (window %) [:demographic-sales (:id demographic)] 0)
                                                (:market-share-pool rules))))
                {} demographics)
        dish-entries (keep (fn [player]
                             (when-let [[dish-id sales]
                                        (first (sort-by (juxt (comp - val) (comp str key))
                                                        (:dish-sales (window player))))]
                               {:id (:id player) :dish-id dish-id :sales sales}))
                           players)
        market-awards (add-award market-awards :best-selling-dish
                                 (split-pool dish-entries :sales (:best-dish-pool rules) true))
        market-awards (add-award market-awards :portions
                                 (ranked-points players #(get (window %) :sales 0)
                                                (:ranking-points rules)))
        market-awards (add-award market-awards :profit
                                 (ranked-points players #(get (window %) :profit 0)
                                                (:ranking-points rules)))
        market-awards (add-award market-awards :regulars
                                 (ranked-points players #(get (window %) :regular-sales 0)
                                                (:regulars-points rules)))
        research-awards
        (reduce (fn [awards branch]
                  (add-award awards :research-leadership
                             (split-pool players #(get-in % [:research branch] 0)
                                         (:research-branch-pool rules) true)))
                market-awards (keys (get-in config [:research :items])))
        all-awards
        (reduce (fn [awards branch]
                  (add-award awards :black-market-leadership
                             (split-pool players #(get-in % [:black-market branch] 0)
                                         (:black-market-branch-pool rules) true)))
                research-awards (keys (get-in config [:black-market :items])))]
    all-awards))

(defn apply-round
  [players awards tick]
  (mapv (fn [player]
          (let [player-awards (get awards (:id player) {})
                total (reduce + 0 (vals player-awards))]
            (-> player
                (update :prestige (fnil + 0) total)
                (update-in [:game-stats :prestige-by-source]
                           #(merge-with + (or % {}) player-awards))
                (assoc-in [:game-stats :latest-score-round]
                          {:tick tick :awards player-awards :total total})
                (assoc-in [:game-stats :score-window] empty-window))))
        players))

(comment
  (score-round {:prestige {:market-share-pool 2 :best-dish-pool 2
                            :ranking-points [3 1] :regulars-points [4 2]
                            :research-branch-pool 1 :black-market-branch-pool 1}}
               [{:id :a :game-stats {:score-window {:sales 4}}}
                {:id :b :game-stats {:score-window {:sales 2}}}]
               []))
