(ns dev.lhrb.pommes.pommes-connection.game-repository
  (:require
   [clojure.edn :as edn]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(def ^:private query-options
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- encode
  [value]
  (pr-str value))

(defn- decode
  [value]
  (edn/read-string value))

(defn enable-foreign-keys!
  [connectable]
  (jdbc/execute-one! connectable ["PRAGMA foreign_keys = ON"] query-options))

(defn create-game!
  [connectable name]
  (jdbc/execute-one! connectable
                     ["INSERT INTO games (name) VALUES (?)" name]
                     query-options)
  (:id (jdbc/execute-one! connectable
                          ["SELECT id FROM games WHERE name = ?" name]
                          query-options)))

(defn add-game-player!
  [connectable {:keys [game-id player-id money prestige interior effects dishes]}]
  (jdbc/execute-one! connectable
                     ["INSERT INTO game_players
                       (game_id, player_id, money, prestige, interior, active_effects)
                       VALUES (?, ?, ?, ?, ?, ?)"
                      game-id player-id (double (or money 20.0))
                      (double (or prestige 0.0)) (name interior)
                      (encode (or effects []))]
                     query-options)
  (let [game-player-id
        (:id (jdbc/execute-one! connectable
                                ["SELECT id FROM game_players
                                  WHERE game_id = ? AND player_id = ?"
                                 game-id player-id]
                                query-options))]
    (doseq [dish dishes]
      (jdbc/execute-one! connectable
                         ["INSERT INTO player_dishes
                           (game_player_id, name, size, portion, seasoning, topping, price)
                           VALUES (?, ?, ?, ?, ?, ?, ?)"
                          game-player-id
                          (:name dish)
                          (name (get-in dish [:pommes :size]))
                          (double (get-in dish [:pommes :portion]))
                          (name (get-in dish [:pommes :seasoning]))
                          (name (:topping dish))
                          (double (:price dish))]
                         query-options))
    game-player-id))

(defn- load-dishes
  [connectable game-player-id]
  (mapv (fn [row]
          {:id (:id row)
           :name (:name row)
           :pommes {:size (keyword (:size row))
                    :portion (:portion row)
                    :seasoning (keyword (:seasoning row))}
           :topping (keyword (:topping row))
           :price (:price row)})
        (jdbc/execute! connectable
                       ["SELECT id, name, size, portion, seasoning, topping, price
                         FROM player_dishes WHERE game_player_id = ? ORDER BY id"
                        game-player-id]
                       query-options)))

(defn load-game-state
  [connectable game-id simulation-config]
  (let [game (jdbc/execute-one! connectable
                                ["SELECT id, tick, market_state FROM games WHERE id = ?" game-id]
                                query-options)]
    (when-not game
      (throw (ex-info "Game does not exist" {:game-id game-id})))
    (let [players (jdbc/execute! connectable
                                 ["SELECT id, money, prestige, interior, active_effects
                                   FROM game_players WHERE game_id = ? ORDER BY id"
                                  game-id]
                                 query-options)]
      (merge simulation-config
             {:game-id game-id
              :tick (:tick game)
              :market (decode (:market-state game))
              :players
              (mapv (fn [player]
                      {:id (:id player)
                       :money (:money player)
                       :prestige (:prestige player)
                       :interior (keyword (:interior player))
                       :effects (decode (:active-effects player))
                       :dishes (load-dishes connectable (:id player))})
                    players)}))))

(defn load-player-game-overview
  "Loads the persisted state needed by one player's game page.

  Returns nil when the game-player is not part of the requested game."
  [connectable game-id game-player-id simulation-config]
  (let [game-player
        (jdbc/execute-one! connectable
                           ["SELECT game_players.id, game_players.money,
                                    game_players.prestige, game_players.interior,
                                    game_players.active_effects, players.name AS player_name
                              FROM game_players
                              JOIN players ON players.id = game_players.player_id
                              WHERE game_players.game_id = ? AND game_players.id = ?"
                            game-id game-player-id]
                           query-options)]
    (when game-player
      (let [game (jdbc/execute-one! connectable
                                    ["SELECT id, name, tick FROM games WHERE id = ?" game-id]
                                    query-options)
            players (jdbc/execute! connectable
                                  ["SELECT game_players.id, players.name AS player_name,
                                           game_players.money, game_players.prestige
                                    FROM game_players
                                    JOIN players ON players.id = game_players.player_id
                                    WHERE game_players.game_id = ?
                                    ORDER BY game_players.id"
                                   game-id]
                                  query-options)
            latest-tick (jdbc/execute-one! connectable
                                           ["SELECT tick, summary
                                             FROM game_ticks
                                             WHERE game_id = ?
                                             ORDER BY tick DESC
                                             LIMIT 1"
                                            game-id]
                                           query-options)]
        {:game game
         :viewer {:id (:id game-player)
                  :name (:player-name game-player)
                  :money (:money game-player)
                  :prestige (:prestige game-player)
                  :interior (keyword (:interior game-player))
                  :effects (decode (:active-effects game-player))
                  :dishes (load-dishes connectable (:id game-player))}
         :players (mapv #(assoc % :name (:player-name %)) players)
         :city (:city simulation-config)
         :latest-tick (when latest-tick
                        (assoc (decode (:summary latest-tick))
                               :tick (:tick latest-tick)))}))))

(defn schedule-setup!
  [datasource game-player-id setup]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [game (jdbc/execute-one! tx
                                  ["SELECT games.tick
                                    FROM games
                                    JOIN game_players ON game_players.game_id = games.id
                                    WHERE game_players.id = ?"
                                   game-player-id]
                                  query-options)]
      (when-not game
        (throw (ex-info "Game player does not exist" {:game-player-id game-player-id})))
      (let [apply-at-tick (inc (:tick game))]
        (jdbc/execute-one! tx
                           ["INSERT INTO scheduled_actions
                             (game_player_id, kind, payload, apply_at_tick)
                             VALUES (?, 'setup', ?, ?)
                             ON CONFLICT (game_player_id, kind, apply_at_tick)
                             DO UPDATE SET payload = excluded.payload,
                                           created_at = CURRENT_TIMESTAMP"
                            game-player-id (encode setup) apply-at-tick]
                           query-options)
        apply-at-tick))))

(defn load-actions
  [connectable game-id apply-at-tick]
  (mapv (fn [row]
          {:id (:id row)
           :game-player-id (:game-player-id row)
           :kind (keyword (:kind row))
           :payload (decode (:payload row))})
        (jdbc/execute! connectable
                       ["SELECT scheduled_actions.id,
                                scheduled_actions.game_player_id,
                                scheduled_actions.kind,
                                scheduled_actions.payload
                         FROM scheduled_actions
                         JOIN game_players
                           ON game_players.id = scheduled_actions.game_player_id
                         WHERE game_players.game_id = ?
                           AND scheduled_actions.apply_at_tick = ?
                         ORDER BY scheduled_actions.id"
                        game-id apply-at-tick]
                       query-options)))

(defn persist-tick!
  [connectable state action-ids summary]
  (jdbc/execute-one! connectable
                     ["UPDATE games SET tick = ?, market_state = ? WHERE id = ?"
                      (:tick state) (encode (:market state)) (:game-id state)]
                     query-options)
  (doseq [player (:players state)]
    (jdbc/execute-one! connectable
                       ["UPDATE game_players
                         SET money = ?, prestige = ?, interior = ?, active_effects = ?
                         WHERE id = ?"
                        (:money player) (:prestige player) (name (:interior player))
                        (encode (:effects player)) (:id player)]
                       query-options))
  (jdbc/execute-one! connectable
                     ["DELETE FROM player_dishes
                       WHERE game_player_id IN
                         (SELECT id FROM game_players WHERE game_id = ?)"
                      (:game-id state)]
                     query-options)
  (doseq [player (:players state)
          dish (:dishes player)]
    (jdbc/execute-one! connectable
                       ["INSERT INTO player_dishes
                         (game_player_id, name, size, portion, seasoning, topping, price)
                         VALUES (?, ?, ?, ?, ?, ?, ?)"
                        (:id player) (or (:name dish) (str (:id dish)))
                        (name (get-in dish [:pommes :size]))
                        (double (get-in dish [:pommes :portion]))
                        (name (get-in dish [:pommes :seasoning]))
                        (name (:topping dish)) (double (:price dish))]
                       query-options))
  (doseq [action-id action-ids]
    (jdbc/execute-one! connectable
                       ["DELETE FROM scheduled_actions WHERE id = ?" action-id]
                       query-options))
  (jdbc/execute-one! connectable
                     ["INSERT INTO game_ticks (game_id, tick, summary) VALUES (?, ?, ?)"
                      (:game-id state) (:tick state) (encode summary)]
                     query-options))

(comment
  ;; (schedule-setup! datasource game-player-id {:interior :punk :dishes [...]})
  )
