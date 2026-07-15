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
          [:header.masthead
           [:p.eyebrow "Pommes Connection · machine room"]
           [:h1 "City desk status"]
           [:p.subhead "The fryer line is being watched."]]
          (layout/panel (if database-ready? "panel--yellow" "panel--quiet")
                        [[:h2 "Service bulletin"]
                         (layout/stat-row "Server" "ready")
                         (layout/stat-row "Database" (if database-ready? "ready" "not ready"))
                         [:p.meta (str "Server: ready · Database: " (if database-ready? "ready" "not ready"))]
                         [:p.meta (if database-ready?
                                    "No inspector nearby."
                                    "Inspector nearby: database needs attention.")]])])
        (response/response)
        (response/content-type "text/html; charset=utf-8")
        (response/status status))))

(comment
  ;; (health-page datasource)
  )
