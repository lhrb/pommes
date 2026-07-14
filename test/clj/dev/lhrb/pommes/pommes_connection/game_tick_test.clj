(ns dev.lhrb.pommes.pommes-connection.game-tick-test
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest is]]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-tick :as game-tick]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.nio.file Files]))

(def query-options {:builder-fn rs/as-unqualified-kebab-maps})

(def simulation-config
  {:rules {:choice-beta 3.0
           :market-memory-alpha 0.1}
   :city {:demographics
          [{:id :locals
            :population 100
            :eat-rate 0.1
            :outside-option-utility 0.2
            :behavior {}
            :preferences {:weights {:size 1.0 :portion 1.0 :seasoning 1.0
                                    :topping 1.0 :price 1.0 :interior 1.0}
                          :size {:small 1.0}
                          :portion {:ideal 1.0 :tolerance 0.5}
                          :seasoning {:salt 1.0}
                          :topping {:ketchup 1.0}
                          :price {:model :cheap :max 8.0}
                          :interior {:classic 1.0 :punk 1.0}}}]}})

(deftest queued-setup-is-applied-by-a-persisted-tick
  (let [database-file (Files/createTempFile "pommes-connection-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database
                         :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (game-repository/enable-foreign-keys! datasource)
      (jdbc/execute-one! datasource ["INSERT INTO players (name) VALUES ('Ada')"] query-options)
      (let [game-id (game-repository/create-game! datasource "Test game")
            player-id (:id (jdbc/execute-one! datasource
                                               ["SELECT id FROM players WHERE name = 'Ada'"]
                                               query-options))
            game-player-id (game-repository/add-game-player!
                             datasource
                             {:game-id game-id
                              :player-id player-id
                              :interior :classic
                              :dishes [{:name "Classic"
                                        :pommes {:size :small :portion 1.0 :seasoning :salt}
                                        :topping :ketchup
                                        :price 4.0}]})]
        (game-repository/schedule-setup!
         datasource game-player-id
         {:interior :punk
          :dishes [{:name "Punk special"
                    :pommes {:size :small :portion 1.1 :seasoning :salt}
                    :topping :ketchup
                    :price 5.0}]})
        (game-repository/schedule-setup!
         datasource game-player-id
         {:interior :punk
          :dishes [{:name "Punk special"
                    :pommes {:size :small :portion 1.1 :seasoning :salt}
                    :topping :ketchup
                    :price 5.5}]})
        (game-tick/run-next-tick! datasource game-id simulation-config)
        (let [game (jdbc/execute-one! datasource ["SELECT tick FROM games WHERE id = ?" game-id]
                                      query-options)
              player (jdbc/execute-one! datasource
                                        ["SELECT interior FROM game_players WHERE id = ?" game-player-id]
                                        query-options)
              dish (jdbc/execute-one! datasource
                                      ["SELECT name, price FROM player_dishes WHERE game_player_id = ?"
                                       game-player-id]
                                      query-options)
              queued (jdbc/execute-one! datasource ["SELECT count(*) AS count FROM scheduled_actions"]
                                         query-options)
              history (jdbc/execute-one! datasource
                                          ["SELECT tick, summary FROM game_ticks WHERE game_id = ?" game-id]
                                          query-options)]
          (is (= 1 (:tick game)))
          (is (= "punk" (:interior player)))
          (is (= "Punk special" (:name dish)))
          (is (= 5.5 (:price dish)))
          (is (zero? (:count queued)))
          (is (= 1 (:tick history)))
          (is (= game-player-id
                 (get-in (edn/read-string (:summary history)) [:players 0 :player-id])))))
      (finally
        (Files/deleteIfExists database-file)))))
