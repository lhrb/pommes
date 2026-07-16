(ns dev.lhrb.pommes.pommes-connection.black-market)

;; This namespace is deliberately pure. Persistence owns upgrade levels while
;; the simulation passes those levels and the current durable player stats in.

(defn progress
  "Diminishing progress in [0, 1). Kept in sync with regular research."
  [level]
  (- 1.0 (Math/exp (/ (- (double level)) 8.0))))

(defn concealment-rate
  [{:keys [items]} black-market]
  (reduce + 0.0
          (for [[item-id level] black-market
                :let [item (get items item-id)]
                :when item]
            (* (progress level)
               (double (:maximum-concealment item 0.0))))))

(defn audit-roll
  "A reproducible roll in [0, 1), so catch-up ticks stay deterministic."
  [player-id tick]
  (/ (double (Math/floorMod (long (hash [player-id tick])) 1000000))
     1000000.0))

(defn- penalty-active? [game-stats]
  (pos? (long (or (:audit-penalty-ticks game-stats) 0))))

(defn- clamp
  [minimum maximum value]
  (max minimum (min maximum value)))

(defn settle-player
  "Applies tax, suspicion, and an optional audit to one player's tick result.

   Returns [updated-player receipt]. `receipt` replaces the gross profit in
   the simulation's latest-tick result with tax-adjusted profit."
  [{:keys [tax-rate suspicion-gain-per-concealment suspicion-decay
           audit-every-ticks base-audit-chance maximum-audit-chance
           audit-penalty-ticks] :as rules}
   tick
   player
   {:keys [revenue profit] :as tick-stats}]
  (let [game-stats (or (:game-stats player) {})
        penalty? (penalty-active? game-stats)
        configured-concealment (concealment-rate rules (:black-market player))
        concealment (if penalty? 0.0 configured-concealment)
        suspicion-before (double (or (:suspicion game-stats) 0.0))
        suspicion-after-decay (clamp 0.0 1.0
                                     (* (+ suspicion-before
                                           (* concealment
                                              (double suspicion-gain-per-concealment)))
                                        (double suspicion-decay)))
        audit-check? (and (not penalty?)
                          (pos? (long audit-every-ticks))
                          (zero? (mod (long tick) (long audit-every-ticks))))
        audit-chance (+ (double base-audit-chance)
                        (* suspicion-after-decay
                           (- (double maximum-audit-chance)
                              (double base-audit-chance))))
        audited? (and audit-check? (< (audit-roll (:id player) tick) audit-chance))
        effective-concealment (if audited? 0.0 concealment)
        effective-tax-rate (* (double tax-rate) (if (or penalty? audited?) 2.0 1.0))
        taxable-revenue (* (double revenue) (- 1.0 effective-concealment))
        tax (* taxable-revenue effective-tax-rate)
        tax-avoided (* (double revenue) effective-concealment (double tax-rate))
        remaining-penalty (cond
                            audited? (dec (long audit-penalty-ticks))
                            penalty? (dec (long (:audit-penalty-ticks game-stats)))
                            :else 0)
        updated-game-stats (-> game-stats
                               (assoc :suspicion (if audited? 0.0 suspicion-after-decay)
                                      :audit-penalty-ticks (max 0 remaining-penalty))
                               (update :total-tax-paid (fnil + 0.0) tax)
                               (update :total-tax-avoided (fnil + 0.0) tax-avoided))
        receipt (assoc tick-stats
                       :gross-profit (double profit)
                       :profit (- (double profit) tax)
                       :concealment-rate effective-concealment
                       :concealed-revenue (* (double revenue) effective-concealment)
                       :taxable-revenue taxable-revenue
                       :tax-rate effective-tax-rate
                       :tax tax
                       :tax-avoided tax-avoided
                       :suspicion (:suspicion updated-game-stats)
                       :audit-chance audit-chance
                       :audited? audited?
                       :audit-penalty-ticks (:audit-penalty-ticks updated-game-stats))]
    [(assoc player :game-stats updated-game-stats) receipt]))

(comment
  (def rules {:tax-rate 0.19 :suspicion-gain-per-concealment 0.10
              :suspicion-decay 0.97 :audit-every-ticks 10
              :base-audit-chance 0.05 :maximum-audit-chance 0.55
              :audit-penalty-ticks 3
              :items {:creative-bookkeeping {:maximum-concealment 0.10}}})
  (def player {:id 1 :black-market {:creative-bookkeeping 10}
               :game-stats {:suspicion 0.0}})
  (settle-player rules 10 player {:revenue 100.0 :profit 60.0}))
