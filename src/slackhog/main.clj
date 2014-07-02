(ns slackhog.main
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [slackhog.config :refer [config]]
            [slackhog.slack :refer [backup]]))

(defn -main [& args]
  (backup (:db config)
          (:token config)
          args)
  (System/exit 0))
