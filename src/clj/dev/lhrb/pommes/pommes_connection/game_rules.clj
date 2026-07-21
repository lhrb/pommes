(ns dev.lhrb.pommes.pommes-connection.game-rules
  (:require [dev.lhrb.pommes.pommes-connection.money :as money]))

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
      (money/round-cents (income-hours-cost config hours)))))

(defn action-cost
  [config action tick]
  (let [phase (game-phase tick)
        reference (double (get-in config [:economy :reference-profit-per-tick phase] 0.0))
        hours (double (get-in action [:cost :income-hours] 0.0))
        minimum (double (get-in config [:actions :cost :minimum] 0.0))]
    (money/round-cents
     (max minimum (* reference (ticks-per-hour config) hours)))))

(defn action-usage-rules
  [config]
  (select-keys (:actions config) [:maximum-uses :reset-ticks]))

(defn running?
  [game]
  (= :running (keyword (:status game))))

(defn finished?
  [game]
  (= :finished (keyword (:status game))))

(comment
  (def config
    {:rules {:tick-seconds 30}
     :economy {:reference-profit-per-tick {:opening 300 :development 600 :late 1000}}
     :research {:cost {:income-hours [2 4 8 14 24]}}
     :actions {:cost {:minimum 10000}
               :maximum-uses 2
               :reset-ticks 1440}})

  (progression-cost config :research 0)
  (action-cost config {:cost {:income-hours 2.0}} 12000))
