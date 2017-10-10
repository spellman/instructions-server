(ns instructions-server.service
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.error :as error-int]
            [ring.util.response :as ring-resp]
            [failjure.core :as f]
            [cheshire.core :as json]
            [suricatta.core :as sql]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]))

(defn iso8601->Instant [s]
  (try
    (-> s (java.time.OffsetDateTime/parse) .toInstant)
    (catch Exception e
      (try
        (-> s (str "Z") (java.time.OffsetDateTime/parse) .toInstant)
        (catch Exception e ::s/invalid)))))

(s/def ::instant-string
  (s/with-gen (s/and string? (s/conformer iso8601->Instant))
    #(s/gen #{"2017-08-20T12:00:00.000-05:00"
              "2017-08-20T12:00:00.000+01:00"
              "2017-08-20T12:00:00.000"
              "2017-08-20T12:00:00.000Z"
              })))

(s/def ::instant
  (s/with-gen #(instance? java.time.Instant %)
    (fn [] (gen/fmap #(.toInstant %)
                     (s/gen (s/inst-in #inst "0000" #inst "4000"))))))

(defn convert-json-map [spec conversion-fn m]
  (let [conformed (s/conform spec m)]
    (if (= conformed ::s/invalid)
      (throw
       (ex-info "Invalid JSON"
                {:type :json-conversion-error
                 :error (s/explain-data spec m)}))
      (conversion-fn conformed))))

(defn sql-quote [s]
  (str "'" s "'"))

(defn sql-quoted [f]
  (comp sql-quote f))



;; APP INSTANCE

(s/def :json.app-instance/app_instance_id string?)
(s/def :json.app-instance/device_manufacturer string?)
(s/def :json.app-instance/device_brand string?)
(s/def :json.app-instance/device_model string?)
(s/def :json.app-instance/time_first_run_occurred ::instant-string)
(s/def :json.app-instance/time_first_run_sent ::instant-string)

(s/def :json/app-instance
  (s/keys :req-un [:json.app-instance/app_instance_id
                   :json.app-instance/device_manufacturer
                   :json.app-instance/device_brand
                   :json.app-instance/device_model
                   :json.app-instance/time_first_run_occurred
                   :json.app-instance/time_first_run_sent
                   ]))

(s/def ::app-instance-id string?)
(s/def ::device-manufacturer string?)
(s/def ::device-brand string?)
(s/def ::device-model string?)
(s/def ::time-first-run-occurred ::instant)
(s/def ::time-first-run-sent ::instant)
(s/def ::time-first-run-received ::instant)

(s/def ::app-instance
  (s/keys :req [::app-instance-id
                ::device-manufacturer
                ::device-brand
                ::device-model
                ::time-first-run-occurred
                ::time-first-run-sent
                ::time-first-run-received
                ]))

(def json-app-instance->app-instance
  (partial convert-json-map
   :json/app-instance
   (fn [conformed]
     {::app-instance-id (:app_instance_id conformed)
      ::device-manufacturer (:device_manufacturer conformed)
      ::device-brand (:device_brand conformed)
      ::device-model (:device_model conformed)
      ::time-first-run-occurred (:time_first_run_occurred conformed)
      ::time-first-run-sent (:time_first_run_sent conformed)
      ::time-first-run-received (java.time.Instant/now)
      })))

(s/fdef json-app-instance->app-instance
        :args (s/cat :json-app-instance :json/app-instance)
        :ret ::app-instance)

(defn make-query-insert-app-instance [app-instance]
  (str "INSERT INTO app_instances
(app_instance_id
,device_manufacturer
,device_brand
,device_model
,time_first_run_occurred
,time_first_run_sent
,time_first_run_received
)
VALUES
("
       (->> app-instance
            ((juxt (sql-quoted ::app-instance-id)
                   (sql-quoted ::device-manufacturer)
                   (sql-quoted ::device-brand)
                   (sql-quoted ::device-model)
                   (sql-quoted ::time-first-run-occurred)
                   (sql-quoted ::time-first-run-sent)
                   (sql-quoted ::time-first-run-received)))
            (string/join ","))
       ");"
       ))

(s/fdef make-query-insert-app-instance
        :args (s/cat :app-instance ::app-instance)
        :ret string?)



;; EVENT

(s/def :json.event/event_type string?)
(s/def :json.event/payload any?)
(s/def :json.event/provenance (s/coll-of string? :into []))
(s/def :json.event/app_instance_id string?)
(s/def :json.event/app_version string?)
(s/def :json.event/os_type string?)
(s/def :json.event/os_version_release string?)
(s/def :json.event/os_version_security_patch string?)
(s/def :json.event/os_sdk
  (s/with-gen (s/and number?
                     (s/conformer #(try (double %) (catch Exception e ::s/invalid)))
                     (s/double-in :infinite? false :min 0.0))
    (fn [] (gen/fmap #(if (neg? %) (* -1.0 %) %) (gen/double)))))
(s/def :json.event/time_occurred ::instant-string)
(s/def :json.event/time_sent ::instant-string)

(s/def :json/event
  (s/keys :req-un [:json.event/event_type
                   :json.event/provenance
                   :json.event/app_instance_id
                   :json.event/app_version
                   :json.event/os_type
                   :json.event/os_version_release
                   :json.event/os_version_security_patch
                   :json.event/os_sdk
                   :json.event/time_occurred
                   :json.event/time_sent
                   ]
          :opt-un [:json.event/payload]))

(s/def ::event-type string?)
(s/def ::payload any?)
(s/def ::provenance (s/coll-of string? :kind vector?))
(s/def ::app-instance-id string?)
(s/def ::app-version string?)
(s/def ::os-type string?)
(s/def ::os-version-release string?)
(s/def ::os-version-security-patch string?)
(s/def ::os-sdk (s/double-in :infinite? false :min 0.0))
(s/def ::time-occurred ::instant)
(s/def ::time-sent ::instant)
(s/def ::time-received ::instant)

(s/def ::event
  (s/keys :req [::event-type
                ::provenance
                ::app-instance-id
                ::app-version
                ::os-type
                ::os-version-release
                ::os-version-security-patch
                ::os-sdk
                ::time-occurred
                ::time-sent
                ::time-received
                ]
          :opt [::payload]))

(def json-event->event
  (partial convert-json-map
           :json/event
           (fn [conformed]
             {::event-type (:event_type conformed)
              ::payload (:payload conformed)
              ::provenance (:provenance conformed)
              ::app-instance-id (:app_instance_id conformed)
              ::app-version (:app_version conformed)
              ::os-type (:os_type conformed)
              ::os-version-release (:os_version_release conformed)
              ::os-version-security-patch (:os_version_security_patch conformed)
              ::os-sdk (:os_sdk conformed)
              ::time-occurred (:time_occurred conformed)
              ::time-sent (:time_sent conformed)
              ::time-received (java.time.Instant/now)
              })))

(s/fdef json-event->event
        :args (s/cat :json-event :json/event)
        :ret ::event)

(defn make-query-insert-event [event]
  (str "INSERT INTO events
(event_type
,payload
,provenance
,app_instance_id
,app_version
,os_type
,os_version_release
,os_version_security_patch
,os_sdk
,time_occurred
,time_sent
,time_received
)
VALUES
("
       (->> event
            ((juxt (sql-quoted ::event-type)
                   (sql-quoted (comp json/encode ::payload))
                   (sql-quoted (comp json/encode ::provenance))
                   (sql-quoted ::app-instance-id)
                   (sql-quoted ::app-version)
                   (sql-quoted ::os-type)
                   (sql-quoted ::os-version-release)
                   (sql-quoted ::os-version-security-patch)
                   ::os-sdk
                   (sql-quoted ::time-occurred)
                   (sql-quoted ::time-sent)
                   (sql-quoted ::time-received)))
            (string/join ","))
       ");"
       ))

(s/fdef make-query-insert-event
        :args (s/cat :event ::event)
        :ret string?)



;; WEB APP

(defn handle-json-conversion-error [e context]
  (if (= :json-conversion-error (-> e ex-data :type))
    (assoc context
           :response {:status 400
                      :headers {"Content-Type" "application/json"}
                      :body (-> e ex-data json/encode)})
    (throw e)))

(def database-interceptor
  {:name ::database-interceptor
   :leave (fn [context]
            (when-let [[op & args] (:tx-data context)]
              #_(println
                 (string/join " "
                              (conj
                               (into ["(apply" op (::db-connection context)] args)
                               ")")
                              ))
              #_(println context)
              (with-open [ctx (sql/context (::db-connection context))]
                (apply op ctx args))
              )
            context)})

(def service-error-handler
  (error-int/error-dispatch
   [context ex]

   [{:exception-type :org.jooq.exception.DataAccessException}]
   (assoc context :response
          {:status 400
           :body (json/encode {:type :database-insertion-error
                               :error "Could not log data."})})

   :else
   (assoc context :io.pedestal.interceptor.chain/error ex)))

(def common-interceptors
  [service-error-handler
   (body-params/body-params)
   http/html-body
   ])

;; TODO: Either pattern-match on the ex-data :type value or use Failjure's
;; either/result monad. I don't like the way results work with spec because
;; checking a conformed result means checking
;; 1. Ok
;; 2. Err
;; 3. ::spec/invalid
;; However, I'm only conforming inside of the json-conversion functions; I will
;; assume that the return values of those functions meet the respective specs.
(def log-app-instance
  {:name ::log-app-instance
   :enter (fn [context]
            (try
              (let [query (-> context
                              (get-in [:request :json-params])
                              json-app-instance->app-instance
                              make-query-insert-app-instance)]
                (assoc context
                       :response {:status 202}
                       :tx-data [sql/fetch query]))
              (catch Exception e (handle-json-conversion-error e context))))})

(def log-event
  {:name ::log-event
   :enter (fn [context]
            (try
              (let [query (-> context
                              (get-in [:request :json-params])
                              (json-event->event)
                              (make-query-insert-event))]
                (assoc context
                       :response {:status 202}
                       :tx-data [sql/fetch query]))
              (catch Exception e (handle-json-conversion-error e context))))})

(def routes #{["/log-app-instance" :post
               (conj common-interceptors database-interceptor log-app-instance)
               ]
              ["/log-event" :post
               (conj common-interceptors database-interceptor log-event)
               ]
              })

(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
