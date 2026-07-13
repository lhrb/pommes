(ns dev.lhrb.pommes.pommes-connection.env
  (:require
    [clojure.tools.logging :as log]
    [dev.lhrb.pommes.pommes-connection.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[pommes-connection starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[pommes-connection started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[pommes-connection has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
