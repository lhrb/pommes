(ns dev.lhrb.pommes.pommes-connection.routes
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.dish-market :as dish-market]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-tick :as game-tick]
   [dev.lhrb.pommes.pommes-connection.views.access :as access-view]
   [dev.lhrb.pommes.pommes-connection.views.dashboard :as dashboard]
   [dev.lhrb.pommes.pommes-connection.views.dishes :as dishes-view]
   [dev.lhrb.pommes.pommes-connection.views.game :as game-view]
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [hiccup2.core :as h]
   [ring.util.response :as response]))

(defn- choice [value options]
  (let [candidate (some-> value str keyword)]
    (when (some #{candidate} options) candidate)))

(defn- number [value]
  (try
    (let [parsed (Double/parseDouble (str value))]
      (when (Double/isFinite parsed) parsed))
    (catch Exception _ nil)))

(defn- dish-form [params index]
  (let [prefix (str "dish-" index "-")
        options (dishes-view/setup-options)]
    {:name (get params (keyword (str prefix "name")))
     :pommes {:size (choice (get params (keyword (str prefix "size"))) (:sizes options))
              :seasoning (choice (get params (keyword (str prefix "seasoning"))) (:seasonings options))}
     :topping (choice (get params (keyword (str prefix "topping"))) (:toppings options))
     :price (get params (keyword (str prefix "price")))}))

(defn- setup-from-params [params]
  (let [options (dishes-view/setup-options)
        dishes (mapv #(dish-form params %) (range 1 (inc (:dish-count options))))
        interior (choice (:interior params) (:interiors options))
        dish-errors
        (into {}
              (keep-indexed
               (fn [index dish]
                 (let [price (number (:price dish))
                       errors (cond-> []
                                (str/blank? (:name dish)) (conj "Name is required.")
                                (nil? (get-in dish [:pommes :size])) (conj "Choose a valid size.")
                                (nil? (get-in dish [:pommes :seasoning])) (conj "Choose a valid seasoning.")
                                (nil? (:topping dish)) (conj "Choose a valid topping.")
                                (or (nil? price) (neg? price)) (conj "Price must be zero or greater."))]
                   (when (seq errors) [(inc index) errors])))
               dishes))
        errors (cond-> dish-errors
                 (nil? interior) (assoc :interior ["Choose a valid interior."]))]
    {:form {:interior interior :dishes dishes}
     :errors errors
     :setup (when (empty? errors)
              {:interior interior
               :dishes (mapv (fn [dish]
                                (-> dish
                                    (update :name str/trim)
                                    (assoc :price (number (:price dish)))))
                              dishes)})}))

(defn- overview [datasource game-token game-player-id]
  (game-repository/load-player-game-overview-by-token datasource game-token game-player-id
                                             game-tick/default-simulation-config))

(defn- form-values [request]
  {:game-name (some-> (get-in request [:params :game-name]) str/trim)
   :player-name (some-> (get-in request [:params :player-name]) str/trim)
   :password (get-in request [:params :player-password])})

(defn- valid-entry? [{:keys [game-name player-name password]} game-name-required?]
  (and (or (not game-name-required?) (seq game-name))
       (seq player-name)
       (seq password)))

(defn- entry-errors [values game-name-required?]
  (cond-> []
    (and game-name-required? (str/blank? (:game-name values))) (conj "Game name is required.")
    (str/blank? (:player-name values)) (conj "Player name is required.")
    (str/blank? (:password values)) (conj "Password is required.")))

(defn- signed-in-redirect [game-token game-player-id]
  (assoc (response/redirect (str "/games/" game-token))
         :session {:game-token game-token :game-player-id game-player-id}))

(defn- player-page [datasource request]
  (let [game-token (get-in request [:path-params :game-token])
        game-player-id (get-in request [:session :game-player-id])]
    (if-let [data (and (= (game-repository/normalize-token game-token)
                          (get-in request [:session :game-token]))
                       game-player-id
                       (overview datasource game-token game-player-id))]
      (game-view/page (assoc data
                             :scheduled-for (get-in request [:params :scheduled-for])
                             :anti-forgery-token (:anti-forgery-token request)
                             :server-now (System/currentTimeMillis)))
      (response/status (response/response "Log in to access this game.") 401))))

(defn- authorized-data [datasource request]
  (let [game-token (get-in request [:path-params :game-token])
        game-player-id (get-in request [:session :game-player-id])]
    (when (and (= (game-repository/normalize-token game-token)
                  (get-in request [:session :game-token]))
               game-player-id)
      (overview datasource game-token game-player-id))))

(defn- forecast-for [datasource data setup]
  (dish-market/forecast
   (game-repository/load-game-state datasource (get-in data [:game :id])
                                   game-tick/default-simulation-config)
   (get-in data [:viewer :id])
   setup))

(defn- dishes-page [datasource request]
  (if-let [data (authorized-data datasource request)]
    (let [setup (select-keys (:viewer data) [:interior :dishes])]
      (dishes-view/page (assoc data
                               :forecast (forecast-for datasource data setup)
                               :scheduled-for (get-in request [:params :scheduled-for])
                               :anti-forgery-token (:anti-forgery-token request))))
    (response/status (response/response "Log in to access this game.") 401)))

(defn- schedule-setup [datasource request]
  (let [game-token (get-in request [:path-params :game-token])
        game-player-id (get-in request [:session :game-player-id])
        data (authorized-data datasource request)]
    (cond
      (nil? data)
      (response/not-found "Game player not found")

      :else
      (let [{:keys [form errors setup]} (setup-from-params (:params request))]
        (if (seq errors)
          (response/status (dishes-view/page (assoc data
                                                   :form form :errors errors
                                                   :forecast (forecast-for datasource data
                                                                            (select-keys (:viewer data) [:interior :dishes]))
                                                   :anti-forgery-token (:anti-forgery-token request)))
                           400)
          (let [apply-at-tick (game-repository/schedule-setup! datasource game-player-id setup)]
            (response/redirect
             (str "/games/" (game-repository/normalize-token game-token)
                  "/dishes?scheduled-for=" apply-at-tick))))))))

(defn- preview-dishes [datasource request]
  (if-let [data (authorized-data datasource request)]
    (let [{:keys [errors setup]} (setup-from-params (:params request))]
      (if (seq errors)
        (response/status (response/response "<p class=\"errors\">Bitte fülle alle Menüfelder gültig aus.</p>") 422)
        (-> (dishes-view/forecast-panel (forecast-for datasource data setup))
            h/html str response/response (response/content-type "text/html; charset=utf-8"))))
    (response/status (response/response "Log in to access this game.") 401)))

(defn- create-game-page [request]
  (access-view/create-page {:anti-forgery-token (:anti-forgery-token request)}))

(defn- create-game [datasource request]
  (let [values (form-values request)]
    (if-not (valid-entry? values true)
      (response/status (access-view/create-page {:form values :errors (entry-errors values true)
                                                 :anti-forgery-token (:anti-forgery-token request)}) 400)
      (let [{:keys [game-token game-player-id]} (game-repository/create-game-with-player!
                                                  datasource (:game-name values) (:player-name values)
                                                  (:password values))]
        (signed-in-redirect game-token game-player-id)))))

(defn- join-game-page [datasource request]
  (if-let [game (game-repository/game-by-invite-token datasource (get-in request [:path-params :invite-token]))]
    (access-view/join-page {:game game :anti-forgery-token (:anti-forgery-token request)})
    (response/not-found "Game invite not found")))

(defn- join-game [datasource request]
  (let [invite-token (get-in request [:path-params :invite-token])
        values (form-values request)
        game (game-repository/game-by-invite-token datasource invite-token)]
    (cond
      (nil? game) (response/not-found "Game invite not found")
      (not (valid-entry? values false))
      (response/status (access-view/join-page {:game game :form values :errors (entry-errors values false)
                                               :anti-forgery-token (:anti-forgery-token request)}) 400)
      :else
      (let [result (game-repository/join-game-with-player! datasource invite-token (:player-name values)
                                                           (:password values))]
        (if (= :player-name-taken (:error result))
          (response/status (access-view/join-page {:game game :form values
                                                   :errors ["That player name is already in this game."]
                                                   :anti-forgery-token (:anti-forgery-token request)}) 400)
          (signed-in-redirect (:game-token result) (:game-player-id result)))))))

(defn- login-token-page [request]
  (let [game-token (get-in request [:params :game-token])]
    (if (seq game-token)
      (response/redirect (str "/login/" (game-repository/normalize-token game-token)))
      (access-view/game-token-page))))

(defn- login-page [datasource request]
  (if-let [game (game-repository/game-by-token datasource (get-in request [:path-params :game-token]))]
    (access-view/login-page {:game game :anti-forgery-token (:anti-forgery-token request)})
    (response/not-found "Game not found")))

(defn- login [datasource request]
  (let [game-token (get-in request [:path-params :game-token])
        values (form-values request)
        game (game-repository/game-by-token datasource game-token)]
    (cond
      (nil? game) (response/not-found "Game not found")
      (not (valid-entry? values false))
      (response/status (access-view/login-page {:game game :form values :errors (entry-errors values false)
                                                :anti-forgery-token (:anti-forgery-token request)}) 400)
      :else
      (if-let [{:keys [game-token game-player-id]}
               (game-repository/authenticate-player datasource game-token (:player-name values) (:password values))]
        (signed-in-redirect game-token game-player-id)
        (response/status (access-view/login-page {:game game :form values
                                                  :errors ["Invalid player name or password."]
                                                  :anti-forgery-token (:anti-forgery-token request)}) 401)))))

(defmethod ig/init-key :app/routes
  [_ {:keys [datasource]}]
  (ring/router
   [["/" {:get (fn [_] (response/redirect "/create-game"))}]
    ["/health" {:get (fn [_request] (dashboard/health-page datasource))}]
    ["/create-game" {:get create-game-page :post (fn [request] (create-game datasource request))}]
    ["/join/:invite-token"
     {:get (fn [request] (join-game-page datasource request))
      :post (fn [request] (join-game datasource request))}]
    ["/login" {:get login-token-page}]
    ["/login/:game-token"
     {:get (fn [request] (login-page datasource request))
      :post (fn [request] (login datasource request))}]
    ["/games/:game-token"
     {:get (fn [request] (player-page datasource request))}]
    ["/games/:game-token/dishes"
     {:get (fn [request] (dishes-page datasource request))}]
    ["/games/:game-token/dishes/setup"
     {:post (fn [request] (schedule-setup datasource request))}]
    ["/games/:game-token/dishes/preview"
     {:post (fn [request] (preview-dishes datasource request))}]]))

(comment
  ;; Add feature routes here as the corresponding feature is implemented.
  )
