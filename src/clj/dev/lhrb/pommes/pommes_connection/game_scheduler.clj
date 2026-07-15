(ns dev.lhrb.pommes.pommes-connection.game-scheduler
  (:require
   [clojure.tools.logging :as log]
   [dev.lhrb.pommes.pommes-connection.game-tick :as game-tick]
   [integrant.core :as ig])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(defn- run-due-ticks!
  [datasource max-ticks-per-game]
  (try
    (game-tick/run-due-ticks! datasource (System/currentTimeMillis)
                              game-tick/default-simulation-config max-ticks-per-game)
    (catch Throwable error
      (log/error error "Unable to run due game ticks"))))

(defmethod ig/init-key :app/game-ticker
  [_ {:keys [datasource poll-millis max-ticks-per-game]
      :or {poll-millis 1000 max-ticks-per-game 10}}]
  (let [executor (Executors/newSingleThreadScheduledExecutor)
        task #(run-due-ticks! datasource max-ticks-per-game)]
    (task)
    (.scheduleWithFixedDelay ^ScheduledExecutorService executor ^Runnable task
                             poll-millis poll-millis TimeUnit/MILLISECONDS)
    {:executor executor}))

(defmethod ig/halt-key! :app/game-ticker
  [_ {:keys [executor]}]
  (.shutdown ^ScheduledExecutorService executor)
  (when-not (.awaitTermination ^ScheduledExecutorService executor 5 TimeUnit/SECONDS)
    (.shutdownNow ^ScheduledExecutorService executor)))

(comment
  ;; Integrant starts this after migrations and stops it before the datasource.
  )
