(ns dev.lhrb.pommes.pommes-connection.test-utils
  (:require
    [dev.lhrb.pommes.pommes-connection.system :as system]
    [peridot.core :as p]
    [byte-streams :as bs]
    [integrant.repl.state :as state]))

(defn system-state
  []
  (or @system/system state/system))

(defn system-fixture
  []
  (fn [f]
    (when (nil? (system-state))
      (system/start-app {:opts {:profile :test}}))
    (f)
    (system/stop-app)))

(defn get-response [ctx]
  (-> ctx
      :response
      (update :body (fnil bs/to-string ""))))

(defn GET [app path params headers]
  (-> (p/session app)
      (p/request path
                 :request-method :get
                 :content-type "application/edn"
                 :headers headers
                 :params params)
      (get-response)))
