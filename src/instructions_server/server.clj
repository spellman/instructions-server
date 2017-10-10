(ns instructions-server.server
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [immuconf.config]
            [io.pedestal.http :as server]
            [io.pedestal.interceptor :as interceptor]
            [integrant.core :as ig]
            [hikari-cp.core :as db-pool]
            [instructions-server.service :as service]))

(def cfg
  (->> ".immuconf.edn"
       io/resource
       slurp
       edn/read-string
       (map #(if (string/starts-with? % "~")
               (string/replace-first % "~" (System/getProperty "user.home"))
               %))
       (apply immuconf.config/load)))

(defn add-database-interceptor [service-map datasource]
  (update service-map ::server/interceptors
          (fn [interceptors]
            (conj interceptors
                  (interceptor/interceptor
                   {:name ::service/add-database
                    :enter (fn [context]
                             (assoc context ::service/db-connection datasource))})))))

(def config
  {::service/service {:service-map (server/default-interceptors service/service)
                      :datasource (ig/ref ::service/database)}
   ::service/database (immuconf.config/get cfg :database)})

(defmethod ig/init-key ::service/service [_ {:keys [service-map datasource]}]
  (do
    (println "service" service-map)
    (-> service-map
        (add-database-interceptor datasource)
        (server/create-server)
        (server/start))))

(defmethod ig/halt-key! ::service/service [_ service-map]
  (server/stop service-map))

(defmethod ig/init-key ::service/database [_ db-spec]
  (db-pool/make-datasource db-spec))

(defmethod ig/halt-key! ::service/database [_ datasource]
  (db-pool/close-datasource datasource))

(defn -main
  [& args]
  (println "\nCreating your server...")
  (ig/init config))
