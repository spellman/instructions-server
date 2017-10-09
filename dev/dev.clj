(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :as test]
            [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.test :as ptest]
            [instructions-server.service :as service]
            [instructions-server.server :as iserver]
            [immuconf.config]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt go init prep reset]]
            [integrant.repl.state :refer [config system]]
            ;; [eftest.runner :as eftest]
            [failjure.core :as f]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [suricatta.core :as sql]))

(def cfg
  (->> ".immuconf.edn"
       io/resource
       slurp
       edn/read-string
       (map #(if (string/starts-with? % "~")
               (string/replace-first % "~" (System/getProperty "user.home"))
               %))
       (apply immuconf.config/load)))

(integrant.repl/set-prep!
 (constantly
  {::service/service
   {:service-map (-> service/service
                     (merge {:env :dev
                             ::server/port 3000
                             ;; do not block thread that starts web server
                             ::server/join? false
                             ;; Routes can be a function that resolve routes,
                             ;;  we can use this to set the routes to be reloadable
                             ::server/routes #(route/expand-routes (deref #'service/routes))
                             ;; all origins are allowed in dev mode
                             ::server/allowed-origins {:creds true
                                                       :allowed-origins (constantly true)}})
                     server/default-interceptors
                     server/dev-interceptors)
    :datasource (ig/ref ::service/database)}
   ::service/database (immuconf.config/get cfg :database)}))

(defn response-for [verb url & options]
  (let [interceptor-service-fn (get-in system [::service/service ::server/service-fn])]
    (apply ptest/response-for interceptor-service-fn verb url options)))

(def test-app-instance-json
  (json/encode
   {:app_instance_id "app-instance-id34104"
    :device_manufacturer "asus"
    :device_brand "google"
    :device_model "Nexus 7"
    :time_first_run_occurred "2017-08-20T12:00:00.000-05:00"
    :time_first_run_sent "2017-08-20T12:00:01.000-05:00"
    }))

(def test-app-instance-map (json/decode test-app-instance-json true))

(def test-event-json
  (json/encode
   {:event_type "set language"
    :payload "english"
    :provenance ["MainActivity" "tab selected by user"]
    :app_instance_id "app-instance-id34104"
    :app_version "5"
    :os_type "android"
    :os_version_release "23.0.0"
    :os_version_security_patch "October 1, 2017"
    :os_sdk 23.0
    :time_occurred "2017-08-20T12:00:00.000-05:00"
    :time_sent "2017-08-20T12:00:01.000-05:00"
    }))

(def test-event-map (json/decode test-event-json true))
