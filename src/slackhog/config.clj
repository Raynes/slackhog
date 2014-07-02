(ns slackhog.config)

(def config
  (let [user (System/getenv "PGUSER")
        pass (System/getenv "PGPASS")
        db {:subprotocol (or (System/getenv "SUBPROTOCOL")
                             "postgresql")
            :subname (or (System/getenv "SUBNAME")
                         "//localhost:5432/slackhog")}
        db (if (and user pass)
             (merge db {:user user
                        :password pass})
             db)]
    {:token (System/getenv "SLACK_TOKEN")
     :db db}))
