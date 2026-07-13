(ns dev.lhrb.pommes.pommes-connection.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[pommes-connection starting]=-"))
   :start      (fn []
                 (log/info "\n-=[pommes-connection started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[pommes-connection has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
