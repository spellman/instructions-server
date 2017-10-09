(set-env!
 :source-paths #{"src" "test"}
 ;; If resource-paths is not set then the clj files will not appear in
 ;; the JAR or uberjar
 :resource-paths #{"src" "config" "resources"}

 ;; these values must be set in order to use the pom task.
 :project 'instructions-server
 :version "0.0.1-SNAPSHOT"

 ;; beware the initial quote on the vector!
 :dependencies '[[org.clojure/clojure "1.9.0-beta2"]
                 [org.clojure/test.check "0.9.0" :scope "test"]
                 [io.pedestal/pedestal.service "0.5.3-SNAPSHOT"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.3-SNAPSHOT"]
                 ;; [io.pedestal/pedestal.immutant "0.5.3-SNAPSHOT"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.3-SNAPSHOT"]
                 [levand/immuconf "0.1.0"]
                 [integrant "0.6.1"]
                 [ragtime "0.7.2"]
                 [mbuczko/boot-ragtime "0.2.0"]
                 [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.22"]
                 [org.slf4j/jcl-over-slf4j "1.7.22"]
                 [org.slf4j/log4j-over-slf4j "1.7.22"]
                 [samestep/boot-refresh "0.1.0" :scope "test"]
                 [adzerk/boot-test "1.1.0" :scope "test"]
                 [failjure "1.2.0"]
                 [cheshire "5.8.0"]
                 [clj-http "3.7.0"]
                 [funcool/suricatta "1.3.1"]
                 [org.postgresql/postgresql "42.1.3"]
                 [hikari-cp "1.8.0"]]
 :checkouts '[[io.pedestal/pedestal.service "0.5.3-SNAPSHOT"]
              [io.pedestal/pedestal.jetty "0.5.3-SNAPSHOT"]])

(boot.core/load-data-readers!)
(require '[clojure.edn :as edn])
(require '[clojure.java.io :as io])
(require '[clojure.string :as string])
(require '[immuconf.config])
(require '[adzerk.boot-test :refer :all])
;; or :refer [test] if you prefer to specify.
(require '[samestep.boot-refresh :refer [refresh]])
(require '[mbuczko.boot-ragtime :refer [ragtime]])
(import 'java.net.URLEncoder)

(let [cfg (->> ".immuconf.edn"
               io/resource
               slurp
               edn/read-string
               (map #(if (string/starts-with? % "~")
                       (string/replace-first % "~" (System/getProperty "user.home"))
                       %))
               (apply immuconf.config/load))
      {:keys [adapter server-name port-number database-name username password]} (immuconf.config/get cfg :database)]
  (task-options!
   ragtime {:database (str "jdbc:" (URLEncoder/encode adapter) "://"
                           (URLEncoder/encode server-name) ":" port-number
                           "/" (URLEncoder/encode database-name)
                           "?user=" (URLEncoder/encode username)
                           "&password=" (URLEncoder/encode password))}))


;; We set the default values for task options. We may override them
;; from the command line or if we call tasks.
(task-options!
 pom {;; needed to write the pom.xml file.
      :project (get-env :project)
      :version (get-env :version)

      ;; How to add in your project license
      ;; :license {"Eclipse Public License"
      ;;           "http://www.eclipse.org/legal/epl-v10.html"}

      ;; And url.
      ;; :url "https://juxt.pro/"
      }

 ;; beware the initial quote here too.
 ;; you could use :all true instead
 aot {:namespace '#{instructions-server.core}}
 jar {:main 'instructions-server.core}

 ;; we have our own dev/user.clj file that we wish to load.  We
 ;; skip-init so that we don't clash with Boot's user ns.
 repl {:init-ns 'user
       :skip-init true})

;; We want to change the behaviour of the repl task to include our own
;; namespace.
(replace-task!
 [r repl] (fn [& xs]
            ;; we only want to include "dev" for the REPL task
            (merge-env! :source-paths #{"dev"})
            ;; we only want to include these tools for the REPL task
            (merge-env! :dependencies '[[io.pedestal/pedestal.service-tools "0.5.3-SNAPSHOT"]
                                        [integrant/repl "0.2.0"]])
            (merge-env! :checkouts '[[io.pedestal/pedestal.service-tools "0.5.3-SNAPSHOT"]])
            (apply r xs)))

(deftask dev
  "Repl with reloading for dev"
  []
  (comp
   (repl)
   (watch :verbose true)
   (refresh)))

(deftask build
  "Build the JAR file"
  [] ;; we have no options for this task.

  ;; compose the tasks.
  (comp
   (aot)
   (pom)
   (jar)))

(deftask build-uber
  "Build the uberjar file"
  []
  (comp
   (aot)
   (pom)
   (uber)
   ;; In this case, we want the jar to be named in a way that mirrors
   ;; the Leiningen way.
   (jar
    :file (format "%s-%s-standalone.jar"
                  (get-env :project)
                  (get-env :version)))))
