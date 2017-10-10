(ns instructions-server.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [instructions-server.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

;; TODO end-to-end tests
