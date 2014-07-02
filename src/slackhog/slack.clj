(ns slackhog.slack
  (:require [clj-http.client :as client]
            [clojure.set :as set]
            [clojure.java.jdbc :as j])
  (:import java.sql.Timestamp))

(def canonical
  {"ims" "im"
   "channels" "channels"
   "groups" "groups"
   "channel-ids" "channels"
   "user-ids" "users"})

(defn slack-fetch
  ([method token] (slack-fetch method token {}))
  ([method token params]
     (-> (client/get (format "https://slack.com/api/%s" method)
                     {:as :json
                      :query-params (assoc params :token token)})
         :body)))

(defn list-chats [token kind]
  ;; Special cases for im
  (let [key (if (= kind "im")
              :ims
              (keyword kind))
        name (if (= kind "im")
               :user
               :name)]
    (->> (slack-fetch (format "%s.list" kind) token)
         key
         (map (juxt name :id))
         (into {}))))

(defn fetch-messages [token channel kind & {:keys [latest oldest]}]
  (let [result (slack-fetch (format "%s.history" kind) token
                            {:channel channel
                             :oldest oldest
                             :latest latest
                             :count 1000}) 
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
                                  {:id (:user message)
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

(defn backup-chats [db token kind]
  (let [channels (map second (list-chats token kind))
        recent (latest db channels)
        messages (messages token recent kind)]
    (insert-messages db messages)))

(defn update-nouns [db to-update nouns kind] 
  (println "Updating" (count to-update) (name kind))
  (doseq [noun to-update
          :let [name (get-in nouns [noun :name])]
          :when (not= name (get-in nouns [noun :name]))]
    (j/update! db kind {:id noun} ["id = ?" noun])))

(defn insert-nouns [db to-insert nouns kind]
  (println "Inserting" (count to-insert) "new" (name kind))
  (apply j/insert! db kind
         (for [noun to-insert]
           (select-keys (nouns noun) [:id :name]))))

(defn delete-nouns [db to-delete nouns kind]
  (println "Deleting" (count to-delete) (name kind))
  (doseq [noun to-delete]
    (j/delete! db kind ["id = ?" noun])))

(defn map-ids [data]
  (into {} (map (juxt :id identity) data)))

(defn backup-nouns [db token kind]
  (let [key-kind (keyword kind)
        old-nouns (map-ids (j/query db [(format "select * from %s;" kind)]))
        old-set (set (keys old-nouns))
        new-nouns (->> (slack-fetch (format "%s.list" kind) token)
                       ((case kind
                           "channels" :channels
                           "users" :members))
                       (map-ids))
        new-set (set (keys new-nouns))
        to-update (for [existing (set/intersection new-set old-set)
                        :let [name (get-in old-nouns [existing :name])]
                        :when (not= (get-in new-nouns [existing :name]))]
                    existing)
        to-insert (set/difference new-set old-set)
        to-delete (set/difference old-set new-set)]
    (when (seq to-update) (update-nouns db to-update new-nouns key-kind))
    (when (seq to-insert) (insert-nouns db to-insert new-nouns key-kind))
    (when (seq to-delete) (delete-nouns db to-delete old-nouns key-kind))))

(defn backup [db token kinds]
  (let [chat-set #{"channels" "groups" "ims"}
        noun-set #{"channel-ids" "user-ids"}]
    (doseq [item kinds
            :let [kind (canonical item)]]
      (if-not ((into chat-set noun-set) item)
        (do (println "Can't backup" kind)
            (System/exit 1))
        (do (println "Fetching" kind)
            (cond (chat-set item) (backup-chats db token kind)
                  (noun-set item) (backup-nouns db token kind)))))))
