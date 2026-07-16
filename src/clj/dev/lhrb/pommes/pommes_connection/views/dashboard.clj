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
         "Systemstatus | Pommes Connection"
         [:main
          [:header.masthead
           [:p.eyebrow "Pommes Connection · Maschinenraum"]
           [:h1 "Lagebericht aus der Frittenzentrale"]
           [:p.subhead "Die Fritteusenleitung wird scharf beobachtet."]]
          (layout/panel (if database-ready? "panel--yellow" "panel--quiet")
                        [[:h2 "Amtliche Durchsage"]
                         (layout/stat-row "Server" "bereit")
                         (layout/stat-row "Datenbank" (if database-ready? "bereit" "nicht bereit"))
                         [:p.meta (str "Server: bereit · Datenbank: " (if database-ready? "bereit" "nicht bereit"))]
                         [:p.meta (if database-ready?
                                    "Kein Kontrolleur weit und breit."
                                    "Kontrolleur in Sicht: Die Datenbank braucht Zuwendung.")]])])
        (response/response)
        (response/content-type "text/html; charset=utf-8")
        (response/status status))))

(comment
  ;; (health-page datasource)
  )
