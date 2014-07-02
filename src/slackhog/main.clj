(ns slackhog.main
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [slackhog.slack :refer [update-messages]]))

(defn -main [& args]
  (let [user (System/getenv "PGUSER")
        pass (System/getenv "PGPASSWORD")
        db {:subprotocol (or (System/getenv "SUBPROTOCOL")
                             "postgresql")
            :subname (or (System/getenv "SUBNAME")
                         "//localhost:5432/slackhog")}
        db (if (and user pass)
             (merge db {:user user
                        :password pass})
             db)
        token (System/getenv "SLACK_TOKEN")]
    (update-messages db token args)))
