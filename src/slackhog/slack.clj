(ns slackhog.slack
  (:require [clj-http.client :as client]
            [clojure.java.jdbc :as j]))

(defn list-chats [token kind]
  ;; Special cases for im
  (let [key (if (= kind "im")
              :ims
              (keyword kind))
        name (if (= kind "im")
               :user
               :name)]
    (->> (client/get (format "https://slack.com/api/%s.list" kind)
                     {:as :json
                      :query-params {:token token}})
         :body
         key
         (map (juxt name :id))
         (into {}))))

(defn fetch-messages [token channel kind & {:keys [latest oldest]}]
  (let [result (:body (client/get
                       (format "https://slack.com/api/%s.history" kind)
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

(defn messages [token channels kind]
  (for [[id oldest] channels]
    [id (only-messages
         (fetch-messages token id kind :oldest oldest))]))

(defn insert-messages [db messages]
  (doseq [[channel messages] messages
          :let [messages (for [message messages]
                           {:username (:user message)
                            :channel channel
                            :text (:text message)
                            :ts (:ts message)})]]
    (println "Inserting" (count messages) "messages for" channel "...")
    (when (seq messages)
      (apply j/insert! db :messages messages))))

(defn most-recent [db channels]
  (into {} (for [channel channels]
             [channel (-> (j/query
                           db
                           ["select ts from messages
                               where channel = ? order by ts desc limit 1;"
                            channel])
                          (first)
                          (:ts))])))

(defn update-messages [db token kinds]
  (doseq [kind kinds]
    (println "Fetching" kind)
    (let [channels (map second (list-chats token kind))
          recent (most-recent db channels)
          messages (messages token recent kind)]
      (insert-messages db messages))))
