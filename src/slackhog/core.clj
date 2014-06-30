(ns slackhog.core
  (:require [clj-http.client :as client]
            [clojure.java.jdbc :as j]))

(defn list-channels [token]
   (->> (client/get "https://slack.com/api/channels.list"
                    {:as :json
                     :query-params {:token token}})
         :body
         :channels
         (map (juxt :name :id))
         (into {})))

(defn fetch-messages [token channel & {:keys [latest oldest]}]
  (let [result (:body (client/get
                       "https://slack.com/api/channels.history"
                       {:as :json
                        :query-params {:token token
                                       :channel channel
                                       :oldest oldest
                                       :latest latest
                                       :count 1000}}))
        messages (:messages result)]
    (if (:has_more result)
      (lazy-seq (concat messages 
                        (fetch-messages token channel
                                        :latest (-> messages last :ts))))
      messages)))

(defn only-messages [messages]
  (remove :subtype messages))

(defn messages [token channels]
  (for [[id oldest] channels]
    [id (only-messages
         (fetch-messages token id :oldest oldest))]))

(defn insert-messages [db messages]
  (doseq [[channel messages] messages
          :let [messages (for [message messages]
                           {:username (:user message)
                            :channel channel
                            :text (:text message)
                            :ts (:ts message)})]]
    (println "Inserting" (count messages) "messages for" channel "...")
    (when (seq messages)
      (apply j/insert! db :messages
             (map #(assoc % :channel channel) messages)))))

(defn most-recent [db channels]
  (into {} (for [channel channels]
             [channel (-> (j/query
                           db
                           ["select ts from messages
                               where channel = ? order by ts desc limit 1;"
                            channel])
                          (first)
                          (:ts))])))

(defn update-messages [db token]
  (let [channels (map second (list-channels token))
        recent (most-recent db channels)
        messages (messages token recent)]
    (insert-messages db messages)))
