(ns dev.lhrb.pommes.pommes-connection.routes
  (:require
   [dev.lhrb.pommes.pommes-connection.views.dashboard :as dashboard]
   [integrant.core :as ig]
   [reitit.ring :as ring]))

(defmethod ig/init-key :app/routes
  [_ {:keys [datasource]}]
  (ring/router
   [["/health" {:get (fn [_request] (dashboard/health-page datasource))}]]))

(comment
  ;; Add feature routes here as the corresponding feature is implemented.
  )
