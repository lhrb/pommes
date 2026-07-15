(ns dev.lhrb.pommes.pommes-connection.routes
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-repository :as game-repository]
   [dev.lhrb.pommes.pommes-connection.game-tick :as game-tick]
   [dev.lhrb.pommes.pommes-connection.views.dashboard :as dashboard]
   [dev.lhrb.pommes.pommes-connection.views.game :as game-view]
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [ring.util.response :as response]))

(defn- parse-id [value]
  (try
    (Long/parseLong value)
    (catch Exception _ nil)))

(defn- choice [value options]
  (let [candidate (some-> value str keyword)]
    (when (some #{candidate} options) candidate)))

(defn- number [value]
  (try
    (let [parsed (Double/parseDouble (str value))]
      (when (Double/isFinite parsed) parsed))
    (catch Exception _ nil)))

(defn- dish-form [params index]
  (let [prefix (str "dish-" index "-")]
    {:name (get params (str prefix "name"))
     :pommes {:size (choice (get params (str prefix "size")) game-view/sizes)
              :portion (get params (str prefix "portion"))
              :seasoning (choice (get params (str prefix "seasoning")) game-view/seasonings)}
     :topping (choice (get params (str prefix "topping")) game-view/toppings)
     :price (get params (str prefix "price"))}))

(defn- setup-from-params [params]
  (let [dishes (mapv #(dish-form params %) (range 1 4))
        interior (choice (get params "interior") game-view/interiors)
        dish-errors
        (into {}
              (keep-indexed
               (fn [index dish]
                 (let [portion (number (get-in dish [:pommes :portion]))
                       price (number (:price dish))
                       errors (cond-> []
                                (str/blank? (:name dish)) (conj "Name is required.")
                                (nil? (get-in dish [:pommes :size])) (conj "Choose a valid size.")
                                (nil? (get-in dish [:pommes :seasoning])) (conj "Choose a valid seasoning.")
                                (nil? (:topping dish)) (conj "Choose a valid topping.")
                                (or (nil? portion) (not (pos? portion))) (conj "Portion must be greater than zero.")
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
                                    (assoc-in [:pommes :portion]
                                              (number (get-in dish [:pommes :portion])))
                                    (assoc :price (number (:price dish)))))
                              dishes)})}))

(defn- overview [datasource game-id game-player-id]
  (game-repository/load-player-game-overview datasource game-id game-player-id
                                             game-tick/default-simulation-config))

(defn- player-page [datasource request]
  (let [game-id (parse-id (get-in request [:path-params :game-id]))
        game-player-id (parse-id (get-in request [:path-params :game-player-id]))]
    (if-let [data (and game-id game-player-id (overview datasource game-id game-player-id))]
      (game-view/page (assoc data :scheduled-for (get-in request [:query-params "scheduled-for"])))
      (response/not-found "Game player not found"))))

(defn- schedule-setup [datasource request]
  (let [game-id (parse-id (get-in request [:path-params :game-id]))
        game-player-id (parse-id (get-in request [:path-params :game-player-id]))
        data (and game-id game-player-id (overview datasource game-id game-player-id))]
    (cond
      (nil? data)
      (response/not-found "Game player not found")

      :else
      (let [{:keys [form errors setup]} (setup-from-params (:params request))]
        (if (seq errors)
          (response/status (game-view/page (assoc data :form form :errors errors)) 400)
          (let [apply-at-tick (game-repository/schedule-setup! datasource game-player-id setup)]
            (response/redirect
             (str "/games/" game-id "/players/" game-player-id
                  "?scheduled-for=" apply-at-tick))))))))

(defmethod ig/init-key :app/routes
  [_ {:keys [datasource]}]
  (ring/router
   [["/health" {:get (fn [_request] (dashboard/health-page datasource))}]
    ["/games/:game-id/players/:game-player-id"
     {:get (fn [request] (player-page datasource request))}]
    ["/games/:game-id/players/:game-player-id/setup"
     {:post (fn [request] (schedule-setup datasource request))}]]))

(comment
  ;; Add feature routes here as the corresponding feature is implemented.
  )
