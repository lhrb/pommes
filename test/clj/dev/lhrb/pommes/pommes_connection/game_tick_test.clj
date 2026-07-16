(ns dev.lhrb.pommes.pommes-connection.game-tick-test
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [dev.lhrb.pommes.pommes-connection.black-market :as black-market]
   [dev.lhrb.pommes.pommes-connection.demographics-simulation :as demographics-simulation]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-tick :as game-tick]
   [dev.lhrb.pommes.pommes-connection.regulars-simulation :as regulars]
   [dev.lhrb.pommes.pommes-connection.simulation :as simulation]
   [dev.lhrb.pommes.pommes-connection.routes]
   [dev.lhrb.pommes.pommes-connection.web :as web]
   [integrant.core :as ig]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [peridot.core :as p]
   [ring.middleware.defaults :as defaults]
   [reitit.ring :as ring])
  (:import
   [java.nio.file Files]))

(def query-options {:builder-fn rs/as-unqualified-kebab-maps})

(def simulation-config
  (merge game-data/game-data
         {:rules (merge (:rules game-data/game-data)
                        {:choice-beta 3.0
                         :market-memory-alpha 0.1})
   :city {:demographics
          [{:id :locals
            :population 100
            :eat-rate 0.1
            :outside-option-utility 0.2
            :behavior {}
            :preferences {:weights {:size 1.0 :seasoning 1.0
                                    :topping 1.0 :price 1.0 :interior 1.0}
                          :size {:small 1.0}
                          :seasoning {:salt 1.0}
                          :topping {:ketchup 1.0}
                          :price {:model :cheap :max 8.0}
                          :interior {:classic 1.0 :cozy 1.0}}}]}}))

(deftest global-attraction-affects-every-demographic
  (let [player {:market-modifiers {}
                :effects [{:type :global-attraction :modifier -0.15}
                          {:type :demographic-attraction :demographic :students :modifier 0.10}]}
        student {:id :students}
        family {:id :families}]
    (is (< (Math/abs (+ 0.05 (simulation/temporary-effect-utility player student))) 1.0e-9))
    (is (= -0.15 (simulation/temporary-effect-utility player family)))))

(deftest black-market-taxes-revenue-and-audits-deterministically
  (let [rules {:tax-rate 0.19 :suspicion-factor 0.02 :audit-every-ticks 10
               :base-audit-chance 100.0 :audit-penalty-ticks 10
               :items {:creative-bookkeeping {:maximum-concealment 0.10}}}
        player {:id 7 :black-market {:creative-bookkeeping 25}
                :game-stats {:suspicion 0.0}}
        [after-first receipt] (black-market/settle-player rules 1 player {:revenue 100.0 :profit 60.0})
        [_ audit-receipt] (black-market/settle-player rules 10 after-first {:revenue 100.0 :profit 60.0})]
    (is (< 0.09 (:concealment-rate receipt) 0.10))
    (is (< (:tax receipt) 19.0))
    (is (= (- 60.0 (:tax receipt)) (:profit receipt)))
    (is (pos? (get-in after-first [:game-stats :total-tax-avoided])))
    (is (:audited? audit-receipt))
    (is (= 9 (:audit-penalty-ticks audit-receipt)))
    (is (= 38.0 (:tax audit-receipt)))
    (is (= 0.0 (:suspicion audit-receipt)))))

(deftest action-costs-scale-with-income-and-cash
  (is (= [300000.0 162000.0 112500.0 225000.0 189000.0 162000.0]
         (mapv #(game-repository/action-cost (game-data/action %) 150000.0 108000000.0)
               [:send-punks :rocker-meeting :influencer-visit
                :parking-chaos :review-bomb :neighborhood-festival])))
  (is (= 20.0
         (game-repository/action-cost (game-data/action :send-punks) 0.0 0.0))))

(deftest regulars-are-bounded-and-convert-deterministically
  (let [rules {:base-conversion-chance 0.03 :loss-rate 0.0 :maximum-population-share 0.20}
        items {:potato-quality {:effect :conversion :maximum-modifier 0.30}}
        demographic {:id :locals :population 100 :eat-rate 0.12}
        offer {:dish {:topping :ketchup} :components {:preference-match 0.8}}
        player {:id :ada :regulars {:locals 5} :research {:potato-quality 25}}
        prepared (regulars/prepare-regulars rules items [demographic] [player]
                                            {[:ada :locals] offer})
        finalized (regulars/finalize-regulars rules items prepared [player]
                                              {[:ada :locals] offer} {[:ada :locals] 100})]
    (is (= 20 (regulars/regular-cap 100 rules)))
    (is (< (get-in prepared [:ada :locals :returns]) 5))
    (is (< 5 (get-in finalized [:ada :locals]) 20))))

(deftest black-market-upgrades-are-persisted-and-loaded
  (let [database-file (Files/createTempFile "pommes-connection-black-market-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (game-repository/enable-foreign-keys! datasource)
      (jdbc/execute-one! datasource ["INSERT INTO players (name) VALUES ('Black market Ada')"] query-options)
      (let [game-id (game-repository/create-game! datasource "Black market game")
            player-id (:id (jdbc/execute-one! datasource ["SELECT id FROM players WHERE name = 'Black market Ada'"]
                                                query-options))
            game-player-id (game-repository/add-game-player!
                            datasource (assoc (game-data/starter-setup)
                                              :game-id game-id :player-id player-id
                                              :player-name "Ada"))
            purchase (game-repository/purchase-black-market!
                      datasource game-player-id :creative-bookkeeping)
            overview (game-repository/load-player-game-overview datasource game-id game-player-id
                                                                (game-data/simulation-config))
            game-token (:game-token (jdbc/execute-one! datasource
                                                       ["SELECT game_token FROM games WHERE id = ?" game-id]
                                                       query-options))
            handler (ring/ring-handler (ig/init-key :app/routes {:datasource datasource}))
            page-response (handler {:request-method :get
                                    :uri (str "/games/" game-token "/black-market")
                                    :session {:game-token game-token :game-player-id game-player-id}})]
        (is (= {:ok true :cost 20.0 :level 1} purchase))
        (is (= {:creative-bookkeeping 1} (get-in overview [:viewer :black-market])))
        (is (= 0.0 (get-in overview [:viewer :money])))
        (is (= 200 (:status page-response)))
        (is (re-find #"Schwarzmarkt" (:body page-response)))
        (is (re-find #"Kreative Buchführung" (:body page-response)))
        (is (= :insufficient-funds
               (:error (game-repository/purchase-black-market!
                        datasource game-player-id :cash-only-register)))))
      (finally
        (Files/deleteIfExists database-file)))))

(deftest game-data-catalog-is-covered-by-demographic-preferences
  (let [catalog (:catalog game-data/game-data)
        demographics (get-in game-data/game-data [:city :demographics])
        price-anchors (get-in game-data/game-data [:market :price-anchors])]
    (is (not (contains? (:interiors catalog) :punk)))
    (doseq [demographic demographics]
      (is (contains? price-anchors (:id demographic)))
      (is (= (set (keys (:seasonings catalog)))
             (set (keys (get-in demographic [:preferences :seasoning])))))
      (is (= (set (keys (:toppings catalog)))
             (set (keys (get-in demographic [:preferences :topping])))))
      (is (= (set (keys (:interiors catalog)))
             (set (keys (get-in demographic [:preferences :interior]))))))))

(deftest city-population-growth-and-composition-drift-are-bounded
  (let [city (demographics-simulation/initial-city
              (:city game-data/game-data) 42)
        before-drift (demographics-simulation/evolve-city city 5700)
        after-drift (demographics-simulation/evolve-city city 5760)
        repeated (demographics-simulation/evolve-city city 5760)
        shares (mapv #(/ (double (:population %)) (:population after-drift))
                     (:demographics after-drift))
        share-changes (map #(Math/abs (- %1 %2))
                           shares
                           (mapv #(/ (double (:population %)) (:population before-drift))
                                 (:demographics before-drift)))]
    (is (= 100 (:population city)))
    (is (= 937506 (:population after-drift)))
    (is (= (:population after-drift)
           (reduce + (map :population (:demographics after-drift)))))
    (is (every? #(<= 0.05 % 0.40) shares))
    (is (<= (apply max share-changes) 0.011))
    (is (= after-drift repeated))))

(defn- anti-forgery-token [body]
  (second (re-find #"name=\"__anti-forgery-token\"[^>]*value=\"([^\"]+)\"" body)))

(deftest customer-orders-are-whole-and-deterministic
  (let [choices [{:share 0.4} {:share 0.4} {:share 0.2}]
        allocation (simulation/allocate-orders 4 choices)
        demographic (assoc (first (get-in simulation-config [:city :demographics]))
                           :population 126
                           :eat-rate 0.1)
        player {:id :ada
                :interior :classic
                :market-modifiers {}
                :effects []
                :dishes [{:id :classic
                          :pommes {:size :small :seasoning :salt}
                          :topping :ketchup
                          :price 4.0}]}
        result (simulation/simulate-demographic (:rules simulation-config)
                                                (:catalog simulation-config)
                                                (:market simulation-config)
                                                [player]
                                                demographic)
        allocations (:allocations result)]
    (is (= [2 1 1] (mapv second allocation)))
    (is (= 13 (:potential-demand result)))
    (is (every? #(integer? (:sales %)) allocations))
    (is (= (:potential-demand result)
           (reduce + 0 (map :sales allocations))))
    (is (every? #(= (* (:sales %) (:price %)) (:revenue %))
                (filter #(= :purchase (:kind %)) allocations)))))

(deftest due-games-catch-up-in-bounded-batches
  (let [database-file (Files/createTempFile "pommes-connection-schedule-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database
                         :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (let [game-id (game-repository/create-game! datasource "Scheduled game")
            now (System/currentTimeMillis)
            tick-millis (* 1000 (get-in simulation-config [:rules :tick-seconds]))]
        (jdbc/execute-one! datasource
                           ["UPDATE games SET next_tick_at = ? WHERE id = ?"
                            (- now (* 2 tick-millis)) game-id]
                           query-options)
        (game-tick/run-due-ticks! datasource now simulation-config 2)
        (is (= 2 (:tick (jdbc/execute-one! datasource
                                             ["SELECT tick FROM games WHERE id = ?" game-id]
                                             query-options))))
        (is (game-repository/game-due? datasource game-id now))
        (game-tick/run-due-ticks! datasource now simulation-config 2)
        (is (= 3 (:tick (jdbc/execute-one! datasource
                                             ["SELECT tick FROM games WHERE id = ?" game-id]
                                             query-options))))
        (is (not (game-repository/game-due? datasource game-id now))))
      (finally
        (Files/deleteIfExists database-file)))))

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
                                        :pommes {:size :small :seasoning :salt}
                                        :topping :ketchup
                                        :price 4.0}]})]
        (game-repository/schedule-setup!
         datasource game-player-id
         {:interior :cozy
          :dishes [{:name "Cozy special"
                    :pommes {:size :small :seasoning :salt}
                    :topping :ketchup
                    :price 5.0}]})
        (game-repository/schedule-setup!
         datasource game-player-id
         {:interior :cozy
          :dishes [{:name "Cozy special"
                    :pommes {:size :small :seasoning :salt}
                    :topping :ketchup
                    :price 5.5}]})
        (game-tick/run-next-tick! datasource game-id simulation-config)
        (let [game (jdbc/execute-one! datasource ["SELECT tick, city_state FROM games WHERE id = ?" game-id]
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
                                          query-options)
              game-stats (jdbc/execute-one! datasource
                                            ["SELECT game_stats FROM game_players WHERE id = ?" game-player-id]
                                            query-options)
              overview (game-repository/load-player-game-overview
                        datasource game-id game-player-id simulation-config)
              game-token (:game-token (jdbc/execute-one! datasource
                                                         ["SELECT game_token FROM games WHERE id = ?" game-id]
                                                         query-options))
              handler (ring/ring-handler
                       (ig/init-key :app/routes {:datasource datasource}))
              page-response (handler {:request-method :get
                                      :uri (str "/games/" game-token)
                                      :session {:game-token game-token :game-player-id game-player-id}})
              dishes-response (handler {:request-method :get
                                        :uri (str "/games/" game-token "/dishes")
                                        :session {:game-token game-token :game-player-id game-player-id}})
              actions-response (handler {:request-method :get
                                         :uri (str "/games/" game-token "/actions")
                                         :session {:game-token game-token :game-player-id game-player-id}})
              invalid-setup-response
              (handler {:request-method :post
                        :uri (str "/games/" game-token "/dishes/setup")
                        :session {:game-token game-token :game-player-id game-player-id}
                        :params {:interior "invalid"}})
              setup-response
              (handler {:request-method :post
                        :uri (str "/games/" game-token "/dishes/setup")
                        :session {:game-token game-token :game-player-id game-player-id}
                        :params {:interior "modern"
                                 :dish-1-name "Budget"
                                 :dish-1-size "medium"
                                 :dish-1-seasoning "salt"
                                 :dish-1-topping "ketchup"
                                 :dish-1-price "3.5"
                                 :dish-2-name "Family"
                                 :dish-2-size "extra-large"
                                 :dish-2-seasoning "salt"
                                 :dish-2-topping "mayo"
                                 :dish-2-price "5.0"
                                 :dish-3-name "Special"
                                 :dish-3-size "large"
                                 :dish-3-seasoning "pommesgewuerz"
                                 :dish-3-topping "special"
                                 :dish-3-price "6.0"}})]
          (is (= 1 (:tick game)))
          (is (= 100 (:population (edn/read-string (:city-state game)))))
          (is (= 100 (get-in overview [:city :population])))
          (is (= "cozy" (:interior player)))
          (is (= "Cozy special" (:name dish)))
          (is (= 5.5 (:price dish)))
          (is (zero? (:count queued)))
          (is (= 1 (:tick history)))
          (is (number? (get-in overview [:game :next-tick-at])))
          (is (= game-player-id
                 (get-in (edn/read-string (:summary history)) [:players 0 :player-id])))
          (is (= {:locals (get-in (edn/read-string (:summary history))
                                  [:players 0 :demographic-sales :locals])}
                 (get-in overview [:latest-tick :players 0 :demographic-sales])))
          (is (= (get-in overview [:latest-tick :players 0 :demographic-sales])
                 (get-in (edn/read-string (:game-stats game-stats)) [:demographic-sales])))
          (is (= "Ada" (get-in overview [:players 0 :name])))
          (is (= "Cozy special" (get-in overview [:viewer :dishes 0 :name])))
          (is (= 200 (:status page-response)))
          (is (re-find #"Gerichte &amp; Markt" (:body page-response)))
          (is (re-find #"Aktionen öffnen" (:body page-response)))
          (is (re-find #"data-next-tick-at" (:body page-response)))
          (is (re-find #"data-server-now" (:body page-response)))
          (is (re-find #"automatic-refresh" (:body page-response)))
          (is (re-find #"performance.now" (:body page-response)))
          (is (not (re-find #"Date\\.now" (:body page-response))))
          (is (= 200 (:status dishes-response)))
          (is (= 200 (:status actions-response)))
          (is (re-find #"Send Punks" (:body actions-response)))
          (is (re-find #"Marktprognose" (:body dishes-response)))
          (is (re-find #"Riesenportion \(€1,00\)" (:body dishes-response)))
          (is (re-find #"Wareneinsatz" (:body dishes-response)))
          (is (= 400 (:status invalid-setup-response)))
          (is (re-find #"Name is required" (:body invalid-setup-response)))
          (is (= 302 (:status setup-response)))
          (is (= (str "/games/" game-token "/dishes?scheduled-for=2")
                 (get-in setup-response [:headers "Location"])))))
      (finally
        (Files/deleteIfExists database-file)))))

(deftest action-purchases-are-priced-recorded-and-applied
  (let [database-file (Files/createTempFile "pommes-connection-actions-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (game-repository/enable-foreign-keys! datasource)
      (jdbc/execute-one! datasource ["INSERT INTO players (name) VALUES ('Ada'), ('Bea')"] query-options)
      (let [game-id (game-repository/create-game! datasource "Action game")
            player-ids (mapv :id (jdbc/execute! datasource ["SELECT id FROM players ORDER BY id"] query-options))
            ada (game-repository/add-game-player! datasource
                                                   (assoc (game-data/starter-setup)
                                                          :game-id game-id :player-id (first player-ids)
                                                          :player-name "Ada"))
            bea (game-repository/add-game-player! datasource
                                                   (assoc (game-data/starter-setup)
                                                          :game-id game-id :player-id (second player-ids)
                                                          :player-name "Bea"))
            invalid-punks (game-repository/purchase-action! datasource ada :send-punks ada)
            invalid-rocker (game-repository/purchase-action! datasource ada :rocker-meeting ada)
            festival-self (game-repository/purchase-action! datasource ada :neighborhood-festival ada)
            reset-money-after-festival
            (jdbc/execute-one! datasource ["UPDATE game_players SET money = 20 WHERE id = ?" ada]
                               query-options)
            influencer-self (game-repository/purchase-action! datasource ada :influencer-visit ada)
            reset-money-after-influencer
            (jdbc/execute-one! datasource ["UPDATE game_players SET money = 20 WHERE id = ?" ada]
                               query-options)
            purchase (game-repository/purchase-action! datasource ada :send-punks bea)
            repeat-purchase (game-repository/purchase-action! datasource ada :send-punks bea)]
        (is (= :invalid-target (:error invalid-punks)))
        (is (= :invalid-target (:error invalid-rocker)))
        (is (= {:ok true :cost 20.0 :apply-at-tick 1} festival-self))
        (is (= {:ok true :cost 20.0 :apply-at-tick 1} influencer-self))
        (is (= {:ok true :cost 20.0 :apply-at-tick 1} purchase))
        (is (= :cooldown (:error repeat-purchase)))
        (is (= 0.0 (:money (jdbc/execute-one! datasource
                                                ["SELECT money FROM game_players WHERE id = ?" ada]
                                                query-options))))
        (is (= {:actor-game-player-id ada :target-game-player-id bea
                :action-id "send-punks" :cost 20.0 :purchase-tick 0 :apply-at-tick 1}
               (select-keys (jdbc/execute-one! datasource
                                                ["SELECT actor_game_player_id, target_game_player_id, action_id,
                                                         cost, purchase_tick, apply_at_tick
                                                  FROM game_action_uses WHERE action_id = 'send-punks'"] query-options)
                            [:actor-game-player-id :target-game-player-id :action-id :cost
                             :purchase-tick :apply-at-tick])))
        (game-tick/run-next-tick! datasource game-id simulation-config)
        (let [target (jdbc/execute-one! datasource
                                        ["SELECT active_effects FROM game_players WHERE id = ?" bea]
                                        query-options)]
          (is (= 4 (count (edn/read-string (:active-effects target)))))
          (is (= :demographic-attraction
                 (:type (first (edn/read-string (:active-effects target))))))))
      (finally
        (Files/deleteIfExists database-file)))))

(deftest create-join-and-login-use-game-tokens-and-sessions
  (let [database-file (Files/createTempFile "pommes-connection-access-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database
                         :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (let [handler (ring/ring-handler (ig/init-key :app/routes {:datasource datasource}))
            create-response (handler {:request-method :post
                                      :uri "/create-game"
                                      :params {:game-name "Lunch rush"
                                               :player-name "Ada"
                                               :player-password "secret"}})
            session (:session create-response)
            game-token (:game-token session)
            invite-token (:invite-token (jdbc/execute-one! datasource
                                                           ["SELECT invite_token FROM games WHERE game_token = ?"
                                                            game-token]
                                                           query-options))
            page-response (handler {:request-method :get
                                    :uri (str "/games/" game-token)
                                    :session session})
            join-response (handler {:request-method :post
                                    :uri (str "/join/" invite-token)
                                    :params {:player-name "Bea" :player-password "another-secret"}})
            duplicate-response (handler {:request-method :post
                                         :uri (str "/join/" invite-token)
                                         :params {:player-name "Bea" :player-password "another-secret"}})
            login-response (handler {:request-method :post
                                     :uri (str "/login/" (str/lower-case game-token))
                                     :params {:player-name "Ada" :player-password "secret"}})
            invalid-login-response (handler {:request-method :post
                                             :uri (str "/login/" game-token)
                                             :params {:player-name "Ada" :player-password "wrong"}})]
        (is (re-matches #"[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{6}" game-token))
        (is (= 302 (:status create-response)))
        (is (= (str "/games/" game-token) (get-in create-response [:headers "Location"])))
        (is (= 200 (:status page-response)))
        (is (re-find #"Gerichte &amp; Markt" (:body page-response)))
        (is (= 302 (:status join-response)))
        (is (= 400 (:status duplicate-response)))
        (is (= 302 (:status login-response)))
        (is (= 401 (:status invalid-login-response))))
      (finally
        (Files/deleteIfExists database-file)))))

(deftest site-middleware-parses-keyworded-form-params
  (let [database-file (Files/createTempFile "pommes-connection-web-" ".db"
                                             (make-array java.nio.file.attribute.FileAttribute 0))
        datasource (jdbc/get-datasource (str "jdbc:sqlite:" database-file))]
    (try
      (migratus/migrate {:store :database
                         :db {:datasource datasource}
                         :migration-dir "resources/migrations"})
      (let [app (-> (ring/ring-handler (ig/init-key :app/routes {:datasource datasource}))
                    (defaults/wrap-defaults web/site-defaults))
            create-page (p/request (p/session app) "/create-game" :request-method :get)
            create-token (anti-forgery-token (get-in create-page [:response :body]))
            create-response
            (p/request create-page "/create-game"
                       :request-method :post
                       :params {"__anti-forgery-token" create-token
                                "game-name" "Browser game"
                                "player-name" "Ada"
                                "player-password" "secret"})
            game-token (subs (get-in create-response [:response :headers "Location"])
                              (count "/games/"))
            invite-token (:invite-token (jdbc/execute-one! datasource
                                                        ["SELECT invite_token FROM games WHERE game_token = ?"
                                                         game-token]
                                                        query-options))
            join-page (p/request (p/session app) (str "/join/" invite-token) :request-method :get)
            join-token (anti-forgery-token (get-in join-page [:response :body]))
            join-response
            (p/request join-page (str "/join/" invite-token)
                       :request-method :post
                       :params {"__anti-forgery-token" join-token
                                "player-name" "Bea"
                                "player-password" "another-secret"})
            login-page (p/request (p/session app) (str "/login/" game-token) :request-method :get)
            login-token (anti-forgery-token (get-in login-page [:response :body]))
            login-response
            (p/request login-page (str "/login/" game-token)
                       :request-method :post
                       :params {"__anti-forgery-token" login-token
                                "player-name" "Ada"
                                "player-password" "secret"})
            game-page (p/follow-redirect create-response)
            dishes-page (p/request game-page (str "/games/" game-token "/dishes") :request-method :get)
            setup-token (anti-forgery-token (get-in dishes-page [:response :body]))
            setup-response
            (p/request dishes-page (str "/games/" game-token "/dishes/setup")
                       :request-method :post
                       :params {"__anti-forgery-token" setup-token
                                "interior" "modern"
                                "dish-1-name" "Budget"
                                "dish-1-size" "medium"
                                "dish-1-seasoning" "salt"
                                "dish-1-topping" "ketchup"
                                "dish-1-price" "3.5"
                                "dish-2-name" "Family"
                                "dish-2-size" "extra-large"
                                "dish-2-seasoning" "salt"
                                "dish-2-topping" "mayo"
                                "dish-2-price" "5.0"
                                "dish-3-name" "Special"
                                "dish-3-size" "large"
                                "dish-3-seasoning" "pommesgewuerz"
                                "dish-3-topping" "special"
                                "dish-3-price" "6.0"})]
        (is (seq create-token))
        (is (= 302 (get-in create-response [:response :status])))
        (is (seq join-token))
        (is (= 302 (get-in join-response [:response :status])))
        (is (seq login-token))
        (is (= 302 (get-in login-response [:response :status])))
        (is (= 200 (get-in game-page [:response :status])))
        (is (= 200 (get-in dishes-page [:response :status])))
        (is (seq setup-token))
        (is (= 302 (get-in setup-response [:response :status])))
        (is (re-find #"/dishes[?]scheduled-for=1$"
                     (get-in setup-response [:response :headers "Location"]))))
      (finally
        (Files/deleteIfExists database-file)))))
