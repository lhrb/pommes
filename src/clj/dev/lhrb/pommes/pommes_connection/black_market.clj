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
                :when (or (= :concealment (:effect item))
                          (and (nil? (:effect item)) (:maximum-concealment item)))]
            (* (progress level)
               (double (:maximum-concealment item 0.0))))))

(defn untaxed-income-rate
  [{:keys [items]} black-market]
  (reduce + 0.0
          (for [[item-id level] black-market
                :let [item (get items item-id)]
                :when (= :untaxed-income (:effect item))]
            (* (progress level) (double (:maximum-untaxed-income item 0.0))))))

(defn audit-protection
  [{:keys [items]} black-market]
  (reduce + 0.0
          (for [[item-id level] black-market
                :let [item (get items item-id)]
                :when (= :audit-protection (:effect item))]
            (* (progress level) (double (:maximum-protection item 0.0))))))

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
        configured-untaxed-rate (untaxed-income-rate rules (:black-market player))
        protection (audit-protection rules (:black-market player))
        concealment (if penalty? 0.0 configured-concealment)
        untaxed-rate (if penalty? 0.0 configured-untaxed-rate)
        untaxed-income (* (double revenue) untaxed-rate)
        suspicion-before (double (or (:suspicion game-stats) 0.0))
        risk-rate (+ concealment untaxed-rate)
        suspicion-after-decay (clamp 0.0 1.0
                                     (* (+ suspicion-before
                                           (* risk-rate
                                              (double suspicion-gain-per-concealment)))
                                        (double suspicion-decay)))
        audit-check? (and (not penalty?)
                          (pos? (long audit-every-ticks))
                          (zero? (mod (long tick) (long audit-every-ticks))))
        raw-audit-chance (+ (double base-audit-chance)
                            (* suspicion-after-decay
                               (- (double maximum-audit-chance)
                                  (double base-audit-chance))))
        audit-chance (* raw-audit-chance (- 1.0 protection))
        audited? (and audit-check? (< (audit-roll (:id player) tick) audit-chance))
        effective-concealment (if audited? 0.0 concealment)
        effective-tax-rate (* (double tax-rate) (if (or penalty? audited?) 2.0 1.0))
        taxable-revenue (* (double revenue) (- 1.0 effective-concealment))
        tax (* taxable-revenue effective-tax-rate)
        tax-avoided (* (double revenue) effective-concealment (double tax-rate))
        illicit-this-tick (+ tax-avoided untaxed-income)
        illicit-since-audit (+ (double (:illicit-since-audit game-stats 0.0)) illicit-this-tick)
        gross-since-audit (+ (double (:gross-profit-since-audit game-stats 0.0)) (max 0.0 (double profit)))
        audit-fine (if audited? (min illicit-since-audit gross-since-audit) 0.0)
        remaining-penalty (cond
                            audited? (dec (long audit-penalty-ticks))
                            penalty? (dec (long (:audit-penalty-ticks game-stats)))
                            :else 0)
        updated-game-stats (-> game-stats
                               (assoc :suspicion (if audited? 0.0 suspicion-after-decay)
                                      :audit-penalty-ticks (max 0 remaining-penalty)
                                      :illicit-since-audit (if audit-check? 0.0 illicit-since-audit)
                                      :gross-profit-since-audit (if audit-check? 0.0 gross-since-audit))
                               (update :audit-count (fnil + 0) (if audited? 1 0))
                               (update :total-tax-paid (fnil + 0.0) tax)
                               (update :total-tax-avoided (fnil + 0.0) tax-avoided))
        receipt (assoc tick-stats
                       :gross-profit (double profit)
                       :profit (- (+ (double profit) untaxed-income) tax audit-fine)
                       :concealment-rate effective-concealment
                       :concealed-revenue (* (double revenue) effective-concealment)
                       :taxable-revenue taxable-revenue
                       :tax-rate effective-tax-rate
                       :tax tax
                       :tax-avoided tax-avoided
                       :untaxed-income untaxed-income
                       :audit-fine audit-fine
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
