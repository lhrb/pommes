(ns dev.lhrb.pommes.pommes-connection.system
  (:require
   [clojure.tools.logging :as log]
   [dev.lhrb.pommes.pommes-connection.db]
   [dev.lhrb.pommes.pommes-connection.env :refer [defaults]]
   [dev.lhrb.pommes.pommes-connection.game-scheduler]
   [dev.lhrb.pommes.pommes-connection.routes]
   [dev.lhrb.pommes.pommes-connection.web]
   [integrant.core :as ig]
   [kit.config :as config]
   [kit.edge.server.undertow])
  (:gen-class))

(defonce system (atom nil))

(defn system-config
  [options]
  (config/read-config "system.edn" options))

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> @system ig/halt!))

(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (system-config (or (:opts params) (:opts defaults) {}))
       ig/expand
       ig/init
       (reset! system)))

(defn -main [& _]
  (start-app)
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(do (stop-app) (shutdown-agents)))))

(comment
  (start-app)
  (stop-app))
