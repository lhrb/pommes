(ns dev.lhrb.pommes.pommes-connection.views.dashboard
  (:require
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [next.jdbc :as jdbc]
   [ring.util.response :as response]))

(defn- database-ready?
  [datasource]
  (try
    (= 1 (:ready (jdbc/execute-one! datasource ["select 1 as ready"])))
    (catch Exception _ false)))

(defn health-page
  [datasource]
  (let [database-ready? (database-ready? datasource)
        status          (if database-ready? 200 503)]
    (-> (layout/page
         "Pommes Connection health"
         [:main
          [:h1 "Pommes Connection"]
          [:h2 "Health"]
          [:ul
           [:li "Server: ready"]
           [:li (str "Database: " (if database-ready? "ready" "not ready"))]]])
        (response/response)
        (response/content-type "text/html; charset=utf-8")
        (response/status status))))

(comment
  ;; (health-page datasource)
  )
