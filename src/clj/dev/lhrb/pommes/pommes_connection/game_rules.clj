(ns dev.lhrb.pommes.pommes-connection.game-rules)

(defn game-phase
  [tick]
  (cond
    (<= (long tick) 2880) :opening
    (<= (long tick) 11520) :development
    :else :late))

(defn ticks-per-hour
  [config]
  (quot 3600 (get-in config [:rules :tick-seconds] 30)))

(defn income-hours-cost
  [config income-hours]
  (* (double (get-in config [:economy :reference-profit-per-tick :opening] 0.0))
     (ticks-per-hour config)
     (double income-hours)))

(defn progression-cost
  [config system level]
  (let [hours (get-in config [system :cost :income-hours level])]
    (when hours
      (/ (Math/round (* 100.0 (income-hours-cost config hours))) 100.0))))

(defn action-cost
  [config action tick]
  (let [phase (game-phase tick)
        reference (double (get-in config [:economy :reference-profit-per-tick phase] 0.0))
        hours (double (get-in action [:cost :income-hours] 0.0))
        minimum (double (get-in config [:actions :cost :minimum] 0.0))]
    (/ (Math/round (* 100.0
                      (max minimum (* reference (ticks-per-hour config) hours))))
       100.0)))

(defn running?
  [game]
  (= :running (keyword (:status game))))

(defn finished?
  [game]
  (= :finished (keyword (:status game))))

(comment
  (def config
    {:rules {:tick-seconds 30}
     :economy {:reference-profit-per-tick {:opening 3.0 :development 6.0 :late 10.0}}
     :research {:cost {:income-hours [2 4 8 14 24]}}
     :actions {:cost {:minimum 100.0}}})

  (progression-cost config :research 0)
  (action-cost config {:cost {:income-hours 2.0}} 12000))
