(ns dev.lhrb.pommes.pommes-connection.web
  (:require
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [ring.middleware.defaults :as defaults]
   [ring.middleware.resource :as resource]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response :as response]))

(defn- production? []
  (= "production" (System/getenv "APP_ENV")))

(defn- session-key []
  (let [encoded (System/getenv "SESSION_KEY")]
    (when-not (seq encoded)
      (throw (ex-info "SESSION_KEY must be set in production" {})))
    (let [key (try
                (.decode (java.util.Base64/getDecoder) encoded)
                (catch IllegalArgumentException _ nil))]
      (when-not (= 16 (count key))
        (throw (ex-info "SESSION_KEY must be base64-encoded 16 random bytes" {})))
      key)))

(defn- configured-site-defaults []
  (let [defaults (if (production?)
                   (-> defaults/secure-site-defaults
                       (assoc :proxy true)
                       (assoc-in [:session :store] (cookie-store {:key (session-key)}))
                       (assoc-in [:session :cookie-attrs :same-site] :lax))
                   defaults/site-defaults)]
    (assoc-in defaults [:security :anti-forgery] true)))

(def site-defaults
  (configured-site-defaults))

(defmethod ig/init-key :app/web
  [_ {:keys [router]}]
  (-> (ring/ring-handler
       router
      (ring/create-default-handler
        {:not-found (constantly (response/not-found "Page not found"))}))
      (resource/wrap-resource "public")
      (defaults/wrap-defaults site-defaults)))

(comment
  ;; The handler is an ordinary Ring function.
  )
