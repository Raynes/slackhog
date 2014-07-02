(ns slackhog.slack
  (:require [clj-http.client :as client]
            [clojure.java.jdbc :as j])
  (:import java.sql.Timestamp))

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
                        (fetch-messages token channel kind
                                        :latest (-> messages last :ts))))
      messages)))

(defn only-messages [messages]
  (remove :subtype messages))

(defn messages [token channels kind]
  (for [[id oldest] channels]
    [id (only-messages
         (fetch-messages token id kind :oldest oldest))]))

(defn parse-ts [ts]
  (let [[time suffix] (.split ts "\\.")]
    {:raw_ts ts
     :ts (Timestamp. (* (Long. time) 1000))
     :ts_suffix (Long. suffix)}))

(defn insert-messages [db messages]
  (doseq [[channel messages] messages
          :let [messages (for [message messages]
                           (merge (parse-ts (:ts message))
                                  {:username (:user message)
                                   :channel channel
                                   :text (:text message)}))]]
    (println "Inserting" (count messages) "messages for" channel "...")
    (when (seq messages)
      (apply j/insert! db :messages messages))))

(defn get-times [db channels query]
  (into {}
        (for [channel channels]
          [channel (-> (j/query db [query channel])
                       (first)
                       (:raw_ts))])))

(defn latest [db channels]
  (get-times db channels
             "select raw_ts from messages
                where channel = ?
                order by ts desc
                limit 1;"))

(defn oldest [db channels]
  (get-times db channels
             "select raw_ts from messages
                where channel = ?
                order by ts asc
                limit 1;"))

(defn update-messages [db token kinds]
  (doseq [kind kinds]
    (println "Fetching" kind)
    (let [channels (map second (list-chats token kind))
          recent (latest db channels)
          messages (messages token recent kind)]
      (insert-messages db messages))))
