(ns dev.lhrb.pommes.pommes-connection.game-repository
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.demographics-simulation :as demographics-simulation]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.security MessageDigest SecureRandom]
   [java.util Base64]
   [javax.crypto SecretKeyFactory]
   [javax.crypto.spec PBEKeySpec]))

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

(def ^:private token-alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
(def ^:private secure-random (SecureRandom.))
(def ^:private password-iterations 600000)
(defn normalize-token [token]
  (some-> token str/trim str/upper-case))

(defn- new-token []
  (apply str (repeatedly 6 #(nth token-alphabet (.nextInt secure-random (count token-alphabet))))))

(defn- base64 [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn- unbase64 [value]
  (.decode (Base64/getDecoder) value))

(defn- password-bytes [password salt iterations]
  (let [spec (PBEKeySpec. (.toCharArray ^String password) salt iterations 256)]
    (try
      (.getEncoded (.generateSecret (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256") spec))
      (finally (.clearPassword spec)))))

(defn hash-password [password]
  (let [salt (byte-array 16)]
    (.nextBytes secure-random salt)
    (str "pbkdf2-sha256$" password-iterations "$" (base64 salt) "$"
         (base64 (password-bytes password salt password-iterations)))))

(defn valid-password? [password encoded]
  (try
    (let [[algorithm iterations salt expected] (str/split (or encoded "") #"\$")]
      (and (= algorithm "pbkdf2-sha256")
           (MessageDigest/isEqual (unbase64 expected)
                                  (password-bytes password (unbase64 salt)
                                                  (Long/parseLong iterations)))))
    (catch Exception _ false)))

(defn- insert-game! [connectable name]
  (loop []
    (let [game-token (new-token)
          invite-token (new-token)
          next-tick-at (+ (System/currentTimeMillis)
                          (* 1000 (get-in game-data/game-data [:rules :tick-seconds])))
          game (try
                 (let [row (jdbc/execute-one! connectable
                                               ["INSERT INTO games (name, game_token, invite_token, next_tick_at)
                                                 VALUES (?, ?, ?, ?) RETURNING id"
                                                name game-token invite-token next-tick-at]
                                               query-options)]
                   {:id (:id row) :name name :game-token game-token :invite-token invite-token})
                 (catch java.sql.SQLException error
                   (if (str/includes? (.getMessage error) "UNIQUE constraint failed: games.")
                     nil
                     (throw error))))]
      (or game (recur)))))

(defn create-game!
  [connectable name]
  (:id (insert-game! connectable name)))

(defn add-game-player!
  [connectable {:keys [game-id player-id player-name money prestige interior effects dishes]}]
  (let [player-name (or player-name
                        (:name (jdbc/execute-one! connectable
                                                   ["SELECT name FROM players WHERE id = ?" player-id]
                                                   query-options)))]
  (jdbc/execute-one! connectable
                     ["INSERT INTO game_players
                       (game_id, player_id, player_name, money, prestige, interior, active_effects)
                       VALUES (?, ?, ?, ?, ?, ?, ?)"
                      game-id player-id player-name (double (or money 20.0))
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
                           (game_player_id, name, size, seasoning, topping, price)
                           VALUES (?, ?, ?, ?, ?, ?)"
                          game-player-id
                          (:name dish)
                          (name (get-in dish [:pommes :size]))
                          (name (get-in dish [:pommes :seasoning]))
                          (name (:topping dish))
                          (double (:price dish))]
                         query-options))
    game-player-id)))

(defn- player-name-in-use? [connectable game-id player-name]
  (boolean
   (jdbc/execute-one! connectable
                      ["SELECT game_players.id FROM game_players
                        WHERE game_players.game_id = ? AND game_players.player_name = ?"
                       game-id player-name]
                      query-options)))

(defn- insert-player! [connectable game-id player-name password]
  (:id (jdbc/execute-one! connectable
                          ["INSERT INTO players (name, password_hash) VALUES (?, ?) RETURNING id"
                           (str game-id ":" player-name) (hash-password password)]
                          query-options)))

(defn create-game-with-player!
  [datasource game-name player-name password]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [game (insert-game! tx game-name)
          player-id (insert-player! tx (:id game) player-name password)
          game-player-id (add-game-player! tx (assoc (game-data/starter-setup)
                                                      :game-id (:id game)
                                                      :player-id player-id
                                                      :player-name player-name))]
      (assoc game :game-player-id game-player-id))))

(defn join-game-with-player!
  [datasource invite-token player-name password]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [game (jdbc/execute-one! tx
                                  ["SELECT id, name, game_token, invite_token FROM games WHERE invite_token = ?"
                                   (normalize-token invite-token)]
                                  query-options)]
      (cond
        (nil? game) {:error :game-not-found}
        (player-name-in-use? tx (:id game) player-name) {:error :player-name-taken :game game}
        :else (let [player-id (insert-player! tx (:id game) player-name password)
                    game-player-id (add-game-player! tx (assoc (game-data/starter-setup)
                                                                :game-id (:id game)
                                                                :player-id player-id
                                                                :player-name player-name))]
                (assoc game :game-player-id game-player-id))))))

(defn authenticate-player
  [connectable game-token player-name password]
  (let [row (jdbc/execute-one! connectable
                               ["SELECT games.game_token, game_players.id AS game_player_id,
                                        players.password_hash
                                 FROM games
                                 JOIN game_players ON game_players.game_id = games.id
                                 JOIN players ON players.id = game_players.player_id
                                 WHERE games.game_token = ? AND game_players.player_name = ?"
                                (normalize-token game-token) player-name]
                               query-options)]
    (when (and row (valid-password? password (:password-hash row)))
      {:game-token (:game-token row) :game-player-id (:game-player-id row)})))

(defn- load-dishes
  [connectable game-player-id]
  (mapv (fn [row]
          {:id (:id row)
           :name (:name row)
           :pommes {:size (keyword (:size row))
                    :seasoning (keyword (:seasoning row))}
           :topping (keyword (:topping row))
           :price (:price row)})
        (jdbc/execute! connectable
                       ["SELECT id, name, size, seasoning, topping, price
                         FROM player_dishes WHERE game_player_id = ? ORDER BY id"
                        game-player-id]
                       query-options)))

(defn- load-regulars [connectable game-player-id]
  (into {}
        (map (juxt #(keyword (:demographic-id %)) :count))
        (jdbc/execute! connectable
                       ["SELECT demographic_id, count FROM player_regulars WHERE game_player_id = ?"
                        game-player-id]
                       query-options)))

(defn- load-research [connectable game-player-id]
  (into {}
        (map (juxt #(keyword (:research-id %)) :level))
        (jdbc/execute! connectable
                       ["SELECT research_id, level FROM player_research WHERE game_player_id = ?"
                        game-player-id]
                        query-options)))

(defn- load-black-market [connectable game-player-id]
  (into {}
        (map (juxt #(keyword (:item-id %)) :level))
        (jdbc/execute! connectable
                       ["SELECT item_id, level FROM player_black_market WHERE game_player_id = ?"
                        game-player-id]
                       query-options)))

(defn load-game-state
  [connectable game-id simulation-config]
  (let [game (jdbc/execute-one! connectable
                                ["SELECT id, tick, market_state, city_state, next_tick_at FROM games WHERE id = ?" game-id]
                                query-options)]
    (when-not game
      (throw (ex-info "Game does not exist" {:game-id game-id})))
    (let [players (jdbc/execute! connectable
                                 ["SELECT id, money, prestige, interior, active_effects, game_stats
                                   FROM game_players WHERE game_id = ? ORDER BY id"
                                  game-id]
                                 query-options)]
      (merge simulation-config
             {:game-id game-id
              :tick (:tick game)
              :next-tick-at (:next-tick-at game)
              :market (decode (:market-state game))
              :city (if-let [city-state (:city-state game)]
                      (decode city-state)
                      (demographics-simulation/initial-city (:city simulation-config) game-id))
              :players
              (mapv (fn [player]
                      {:id (:id player)
                       :money (:money player)
                       :prestige (:prestige player)
                       :interior (keyword (:interior player))
                       :effects (decode (:active-effects player))
                       :game-stats (decode (:game-stats player))
                       :regulars (load-regulars connectable (:id player))
                       :research (load-research connectable (:id player))
                       :black-market (load-black-market connectable (:id player))
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
                                    game_players.active_effects, game_players.game_stats,
                                    game_players.player_name
                              FROM game_players
                              JOIN players ON players.id = game_players.player_id
                              WHERE game_players.game_id = ? AND game_players.id = ?"
                            game-id game-player-id]
                           query-options)]
    (when game-player
      (let [game (jdbc/execute-one! connectable
                                    ["SELECT id, name, tick, city_state, next_tick_at FROM games WHERE id = ?" game-id]
                                    query-options)
            players (jdbc/execute! connectable
                                  ["SELECT game_players.id, game_players.player_name,
                                           game_players.money, game_players.prestige
                                    FROM game_players
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
                  :game-stats (decode (:game-stats game-player))
                  :regulars (load-regulars connectable (:id game-player))
                  :research (load-research connectable (:id game-player))
                  :black-market (load-black-market connectable (:id game-player))
                  :dishes (load-dishes connectable (:id game-player))}
         :players (mapv #(assoc % :name (:player-name %)) players)
         :city (if-let [city-state (:city-state game)]
                 (decode city-state)
                 (demographics-simulation/initial-city (:city simulation-config) game-id))
         :latest-tick (when latest-tick
                        (assoc (decode (:summary latest-tick))
                               :tick (:tick latest-tick)))}))))

(defn load-player-game-overview-by-token
  [connectable game-token game-player-id simulation-config]
  (when-let [game (jdbc/execute-one! connectable
                                     ["SELECT id, game_token, invite_token FROM games WHERE game_token = ?"
                                      (normalize-token game-token)]
                                     query-options)]
    (some-> (load-player-game-overview connectable (:id game) game-player-id simulation-config)
            (assoc-in [:game :game-token] (:game-token game))
            (assoc-in [:game :invite-token] (:invite-token game)))))

(defn game-by-invite-token [connectable invite-token]
  (jdbc/execute-one! connectable
                     ["SELECT name, invite_token FROM games WHERE invite_token = ?"
                      (normalize-token invite-token)]
                     query-options))

(defn game-by-token [connectable game-token]
  (jdbc/execute-one! connectable
                     ["SELECT name, game_token FROM games WHERE game_token = ?"
                      (normalize-token game-token)]
                     query-options))

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

(defn action-cost
  [action last-revenue money]
  (let [{:keys [minimum]}
        (get-in game-data/game-data [:actions :cost])
        {:keys [income-multiplier cash-ratio]}
        (:cost action)]
    (/ (Math/round (* 100.0
                      (max (double minimum)
                           (* (double income-multiplier)
                              (double (or last-revenue 0.0)))
                           (* (double cash-ratio)
                              (double (or money 0.0))))))
       100.0)))

(defn progression-cost [cost level]
  (let [{:keys [base multiplier]} cost]
    (/ (Math/round (* 100.0 (double base) (Math/pow (double multiplier) (long level)))) 100.0)))

(defn research-cost [level]
  (progression-cost (get-in game-data/game-data [:research :cost]) level))

(defn research-statuses
  [connectable game-id game-player-id money]
  (let [levels (load-research connectable game-player-id)
        maximum-level (get-in game-data/game-data [:research :cost :maximum-level])
        leaders (into {}
                      (for [[research-id _] (game-data/research-items)]
                        [research-id
                         (jdbc/execute! connectable
                                        ["SELECT game_players.player_name, coalesce(player_research.level, 0) AS level
                                               FROM game_players
                                               LEFT JOIN player_research
                                                 ON player_research.game_player_id = game_players.id
                                                AND player_research.research_id = ?
                                               WHERE game_players.game_id = ?
                                                 AND coalesce(player_research.level, 0) = (SELECT max(coalesce(pr.level, 0))
                                                                                             FROM game_players gp
                                                                                             LEFT JOIN player_research pr
                                                                                               ON pr.game_player_id = gp.id
                                                                                              AND pr.research_id = ?
                                                                                             WHERE gp.game_id = ?)
                                               ORDER BY game_players.player_name"
                                         (name research-id) game-id (name research-id) game-id]
                                        query-options)]))]
    (mapv (fn [[research-id item]]
            (let [level (long (get levels research-id 0))
                  cost (research-cost level)]
              (assoc item :level level :cost cost :maximum-level maximum-level
                     :available? (and (< level maximum-level) (>= (double money) cost))
                     :leaders (get leaders research-id))))
          (game-data/research-items))))

(defn purchase-research!
  [datasource game-player-id research-id]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [item (game-data/research-item research-id)
          maximum-level (get-in game-data/game-data [:research :cost :maximum-level])
          player (jdbc/execute-one! tx
                                    ["SELECT id, money FROM game_players WHERE id = ?" game-player-id]
                                    query-options)
          level (long (get (load-research tx game-player-id) research-id 0))
          cost (research-cost level)]
      (cond
        (nil? player) {:error :player-not-found}
        (nil? item) {:error :unknown-research}
        (>= level maximum-level) {:error :maximum-level}
        (< (double (:money player)) cost) {:error :insufficient-funds :cost cost}
        :else
        (let [updated (jdbc/execute-one! tx
                                         ["UPDATE game_players SET money = money - ? WHERE id = ? AND money >= ?"
                                          cost game-player-id cost]
                                         query-options)]
          (if (zero? (long (or (:next.jdbc/update-count updated) 0)))
            {:error :insufficient-funds :cost cost}
            (do
              (jdbc/execute-one! tx
                                 ["INSERT INTO player_research (game_player_id, research_id, level)
                                   VALUES (?, ?, 1)
                                   ON CONFLICT (game_player_id, research_id)
                                   DO UPDATE SET level = player_research.level + 1"
                                  game-player-id (name research-id)]
                                 query-options)
              {:ok true :cost cost :level (inc level)})))))))

(defn black-market-statuses
  [connectable game-player-id money]
  (let [levels (load-black-market connectable game-player-id)
        config (:black-market game-data/game-data)
        maximum-level (get-in config [:cost :maximum-level])]
    (mapv (fn [[item-id item]]
            (let [level (long (get levels item-id 0))
                  cost (progression-cost (:cost config) level)]
              (assoc item :level level :cost cost :maximum-level maximum-level
                     :available? (and (< level maximum-level) (>= (double money) cost)))))
          (game-data/black-market-items))))

(defn purchase-black-market!
  [datasource game-player-id item-id]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [item (game-data/black-market-item item-id)
          config (:black-market game-data/game-data)
          maximum-level (get-in config [:cost :maximum-level])
          player (jdbc/execute-one! tx ["SELECT id, money FROM game_players WHERE id = ?" game-player-id]
                                    query-options)
          level (long (get (load-black-market tx game-player-id) item-id 0))
          cost (progression-cost (:cost config) level)]
      (cond
        (nil? player) {:error :player-not-found}
        (nil? item) {:error :unknown-item}
        (>= level maximum-level) {:error :maximum-level}
        (< (double (:money player)) cost) {:error :insufficient-funds :cost cost}
        :else
        (let [updated (jdbc/execute-one! tx
                                         ["UPDATE game_players SET money = money - ? WHERE id = ? AND money >= ?"
                                          cost game-player-id cost]
                                         query-options)]
          (if (zero? (long (or (:next.jdbc/update-count updated) 0)))
            {:error :insufficient-funds :cost cost}
            (do
              (jdbc/execute-one! tx
                                 ["INSERT INTO player_black_market (game_player_id, item_id, level)
                                   VALUES (?, ?, 1)
                                   ON CONFLICT (game_player_id, item_id)
                                   DO UPDATE SET level = player_black_market.level + 1"
                                  game-player-id (name item-id)]
                                 query-options)
              {:ok true :cost cost :level (inc level)})))))))

(defn- last-player-revenue
  [connectable game-id game-player-id]
  (when-let [tick (jdbc/execute-one! connectable
                                     ["SELECT summary FROM game_ticks
                                       WHERE game_id = ? ORDER BY tick DESC LIMIT 1"
                                      game-id]
                                     query-options)]
    (some (fn [player]
            (when (= game-player-id (:player-id player))
              (:revenue player)))
          (:players (decode (:summary tick))))))

(defn action-statuses
  [connectable game-id game-player-id tick money]
  (let [last-revenue (or (last-player-revenue connectable game-id game-player-id) 0.0)
        latest-use (into {}
                         (map (juxt #(keyword (:action-id %)) :purchase-tick))
                         (jdbc/execute! connectable
                                        ["SELECT action_id, max(purchase_tick) AS purchase_tick
                                          FROM game_action_uses
                                          WHERE actor_game_player_id = ?
                                          GROUP BY action_id"
                                         game-player-id]
                                        query-options))]
    (mapv (fn [[action-id action]]
            (let [available-at (+ (long (get latest-use action-id -1000000))
                                  (:cooldown action))]
              (assoc action
                     :cost (action-cost action last-revenue money)
                     :available-at-tick available-at
                     :available? (<= available-at tick))))
          (game-data/actions))))

(defn purchase-action!
  "Records and queues an action purchase. Returns either {:ok true ...} or
   {:error keyword}. Money, history, and the scheduled action change together."
  [datasource actor-game-player-id action-id target-game-player-id]
  (jdbc/with-transaction [tx datasource]
    (enable-foreign-keys! tx)
    (let [action (game-data/action action-id)
          actor (jdbc/execute-one! tx
                                   ["SELECT game_players.id, game_players.game_id,
                                            game_players.money, games.tick
                                     FROM game_players JOIN games ON games.id = game_players.game_id
                                     WHERE game_players.id = ?"
                                    actor-game-player-id]
                                   query-options)
          target (when actor
                   (jdbc/execute-one! tx
                                      ["SELECT id FROM game_players WHERE id = ? AND game_id = ?"
                                       target-game-player-id (:game-id actor)]
                                      query-options))]
      (cond
        (nil? actor) {:error :actor-not-found}
        (nil? action) {:error :unknown-action}
        (nil? target) {:error :invalid-target}
        (and (= :other-player (:target action))
             (= actor-game-player-id target-game-player-id)) {:error :invalid-target}
        :else
        (let [tick (:tick actor)
              latest-use (jdbc/execute-one! tx
                                            ["SELECT max(purchase_tick) AS purchase_tick
                                              FROM game_action_uses
                                              WHERE actor_game_player_id = ? AND action_id = ?"
                                             actor-game-player-id (name action-id)]
                                            query-options)
              available-at (+ (long (or (:purchase-tick latest-use) -1000000))
                              (:cooldown action))
              cost (action-cost action
                                (last-player-revenue tx (:game-id actor) actor-game-player-id)
                                (:money actor))]
          (cond
            (> available-at tick) {:error :cooldown :available-at-tick available-at}
            (< (double (:money actor)) cost) {:error :insufficient-funds :cost cost}
            :else
            (let [apply-at-tick (inc tick)
                  update-result (jdbc/execute-one! tx
                                                   ["UPDATE game_players SET money = money - ?
                                                     WHERE id = ? AND money >= ?"
                                                    cost actor-game-player-id cost]
                                                   query-options)]
              (if (zero? (long (or (:next.jdbc/update-count update-result) 0)))
                {:error :insufficient-funds :cost cost}
                (let [action-use-id
                      (:id (jdbc/execute-one! tx
                                              ["INSERT INTO game_action_uses
                                                (game_id, actor_game_player_id, target_game_player_id,
                                                 action_id, cost, purchase_tick, apply_at_tick)
                                                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
                                               (:game-id actor) actor-game-player-id target-game-player-id
                                               (name action-id) cost tick apply-at-tick]
                                              query-options))]
                  (jdbc/execute-one! tx
                                     ["INSERT INTO scheduled_actions
                                       (game_player_id, kind, payload, apply_at_tick)
                                       VALUES (?, ?, ?, ?)"
                                      actor-game-player-id
                                      (str "action-" (name action-id))
                                      (encode {:action-id action-id
                                               :target-game-player-id target-game-player-id
                                               :action-use-id action-use-id})
                                      apply-at-tick]
                                     query-options)
                  {:ok true :cost cost :apply-at-tick apply-at-tick})))))))))

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

(defn due-game-ids
  [connectable now]
  (mapv :id
        (jdbc/execute! connectable
                       ["SELECT id FROM games
                         WHERE next_tick_at <= ?
                         ORDER BY next_tick_at, id"
                        now]
                       query-options)))

(defn game-due?
  [connectable game-id now]
  (boolean
   (jdbc/execute-one! connectable
                      ["SELECT id FROM games WHERE id = ? AND next_tick_at <= ?"
                       game-id now]
                      query-options)))

(defn persist-tick!
  [connectable state action-ids summary]
  (jdbc/execute-one! connectable
                     ["UPDATE games SET tick = ?, market_state = ?, city_state = ?, next_tick_at = ? WHERE id = ?"
                      (:tick state) (encode (:market state)) (encode (:city state))
                      (:next-tick-at state) (:game-id state)]
                     query-options)
  (doseq [player (:players state)]
    (jdbc/execute-one! connectable
                       ["UPDATE game_players
                         SET money = ?, prestige = ?, interior = ?, active_effects = ?, game_stats = ?
                         WHERE id = ?"
                        (:money player) (:prestige player) (name (:interior player))
                        (encode (:effects player)) (encode (:game-stats player)) (:id player)]
                       query-options))
  (jdbc/execute-one! connectable
                     ["DELETE FROM player_regulars WHERE game_player_id IN
                       (SELECT id FROM game_players WHERE game_id = ?)" (:game-id state)]
                     query-options)
  (doseq [player (:players state)
          [demographic-id count] (:regulars player)
          :when (pos? count)]
    (jdbc/execute-one! connectable
                       ["INSERT INTO player_regulars (game_player_id, demographic_id, count) VALUES (?, ?, ?)"
                        (:id player) (name demographic-id) count]
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
                         (game_player_id, name, size, seasoning, topping, price)
                         VALUES (?, ?, ?, ?, ?, ?)"
                        (:id player) (or (:name dish) (str (:id dish)))
                        (name (get-in dish [:pommes :size]))
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
  ;; (schedule-setup! datasource game-player-id {:interior :cozy :dishes [...]})

  ;; Action costs use the highest of the €20 minimum, revenue share, and cash share.
  (action-cost (game-data/action :send-punks) 0.0 0.0)
  ;; => 20.0

  (action-cost (game-data/action :send-punks) 150000.0 108000000.0)
  ;; => 300000.0

  (action-cost (game-data/action :parking-chaos) 150000.0 108000000.0)
  ;; => 225000.0

  (action-cost (game-data/action :review-bomb) 150000.0 108000000.0)
  ;; => 189000.0
  )
