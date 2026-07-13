(ns dev.lhrb.pommes.pommes-connection.web
  (:require
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [ring.middleware.defaults :as defaults]
   [ring.util.response :as response]))

(def site-defaults
  (assoc-in defaults/site-defaults [:security :anti-forgery] false))

(defmethod ig/init-key :app/web
  [_ {:keys [router]}]
  (-> (ring/ring-handler
       router
       (ring/create-default-handler
        {:not-found (constantly (response/not-found "Page not found"))}))
      (defaults/wrap-defaults site-defaults)))

(comment
  ;; The handler is an ordinary Ring function.
  )
