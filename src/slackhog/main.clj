(ns slackhog.main
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [slackhog.config :refer [config]]
            [slackhog.slack :refer [update-messages]]))

(defn -main [& args]
  (update-messages (:db config)
                   (:token config)
                   args))
