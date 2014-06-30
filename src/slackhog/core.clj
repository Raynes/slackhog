(ns slackhog.core
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [slackhog.slack :refer [update-messages]]))

(defn -main [& args]
  (let [db {:subprotocol (or (System/getenv "SUBPROTOCOL")
                             "postgres")
            :subname (or (System/getenv "SUBNAME")
                         "//localhost:5432/slackhog")
            :user (System/getenv "PGUSER")
            :password (System/getenv "PGPASSWORD")}
        token (System/getenv "SLACK_TOKEN")]
    (update-messages db token args)))
