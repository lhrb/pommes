(ns dev.lhrb.pommes.pommes-connection.balancing-simulation-test
  (:require
   [clojure.test :refer [deftest is]]
   [dev.lhrb.pommes.pommes-connection.balancing-simulation :as balancing]))

(deftest balancing-results-are-compact-unless-debugging-is-requested
  (let [compact (balancing/run-game {:seed 42 :ticks 360})
        detailed (balancing/run-game {:seed 42 :ticks 360 :include-final-state? true})]
    (is (= 360 (get-in compact [:history 0 :tick])))
    (is (not (contains? compact :final-state)))
    (is (= 360 (get-in detailed [:final-state :tick])))))

(deftest fast-balancing-is-deterministic
  (let [options {:seed 7 :ticks 720 :mode :fast}
        first-run (balancing/run-game options)
        second-run (balancing/run-game options)]
    (is (= first-run second-run))
    (is (= 720 (get-in first-run [:history 0 :tick])))))

(deftest exact-balancing-keeps-the-real-single-tick-path
  (let [game (balancing/run-game {:seed 42 :ticks 720 :mode :exact})]
    (is (= {:id :bude-1 :strategy :adaptive-menu :activity 4 :prestige 13}
           (:winner game)))
    (is (= 1 (:leader-changes game)))))

(deftest parallel-exact-suite-preserves-serial-results-and-seed-order
  (let [options {:seeds [3 1 2] :ticks 180 :mode :exact}
        serial (balancing/run-suite (assoc options :parallelism 1))
        parallel (balancing/run-suite (assoc options :parallelism 2))]
    (is (= [3 1 2] (mapv :seed parallel)))
    (is (= serial parallel))))

(comment
  ;; The default suite is fast and parallel; use exact mode to confirm a change.
  (time (balancing/run-suite {:seeds (range 1 31)}))
  (balancing/run-suite {:seeds (range 1 4) :mode :exact :parallelism 1}))
