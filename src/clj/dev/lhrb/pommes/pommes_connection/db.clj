(ns dev.lhrb.pommes.pommes-connection.db
  (:require
   [integrant.core :as ig]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]))

(defmethod ig/init-key :app/db
  [_ {:keys [jdbc-url]}]
  (jdbc/get-datasource jdbc-url))

(defmethod ig/init-key :app/migrations
  [_ {:keys [datasource migration-dir]}]
  (migratus/migrate {:store         :database
                     :db            {:datasource datasource}
                     :migration-dir migration-dir})
  datasource)

(comment
  (jdbc/execute-one! (jdbc/get-datasource "jdbc:sqlite::memory:") ["select 1 as ready"]))
