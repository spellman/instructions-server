(ns instructions-server.server
  (:gen-class) ; for -main method in uberjar
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [immuconf.config]
            [io.pedestal.http :as server]
            [io.pedestal.interceptor :as interceptor]
            [integrant.core :as ig]
            [hikari-cp.core :as db-pool]
            [instructions-server.service :as service]))

;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call server/start and server/stop on this service
#_(defonce runnable-service (server/create-server service/service))

#_(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env :dev
              ::server/port 3000
              ;; do not block thread that starts web server
              ::server/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes #(route/expand-routes (deref #'service/routes))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

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
  ;; TODO: Ensure this fn is idempotent.
  (server/stop service-map))

(defmethod ig/init-key ::service/database [_ db-spec]
  (db-pool/make-datasource db-spec))

(defmethod ig/halt-key! ::service/database [_ datasource]
  (db-pool/close-datasource datasource))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  (ig/init config))

;; If you package the service up as a WAR,
;; some form of the following function sections is required (for io.pedestal.servlet.ClojureVarServlet).

;;(defonce servlet  (atom nil))
;;
;;(defn servlet-init
;;  [_ config]
;;  ;; Initialize your app here.
;;  (reset! servlet  (server/servlet-init service/service nil)))
;;
;;(defn servlet-service
;;  [_ request response]
;;  (server/servlet-service @servlet request response))
;;
;;(defn servlet-destroy
;;  [_]
;;  (server/servlet-destroy @servlet)
;;  (reset! servlet nil))

