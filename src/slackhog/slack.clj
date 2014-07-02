(ns slackhog.slack
  (:require [clj-http.client :as client]
            [clojure.set :as set]
            [clojure.java.jdbc :as j])
  (:import java.sql.Timestamp))

(def canonical
  "Canonical API names for things we're backing up."
  {"ims" "im"
   "channels" "channels"
   "groups" "groups"
   "channel-ids" "channels"
   "user-ids" "users"})

(defn slack-fetch
  "Convenience function for doing a slack GET request."
  ([method token] (slack-fetch method token {}))
  ([method token params]
     (-> (client/get (format "https://slack.com/api/%s" method)
                     {:as :json
                      :query-params (assoc params :token token)})
         :body)))

(defn list-chats
  "Get a list of chats. Either channels, ims, or groups."
  [token kind]
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

(defn fetch-messages
  "Fetch messages for a channel, group, or im, truncating as necessary
   depending on :latest and :oldest timestamps"
  [token channel kind & {:keys [latest oldest]}]
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

(defn only-messages
  "Remove any messages with a subtype."
  [messages]
  (remove :subtype messages))

(defn messages
  "Get messages for all targets."
  [token channels kind]
  (for [[id oldest] channels]
    [id (only-messages
         (fetch-messages token id kind :oldest oldest))]))

(defn parse-ts
  "Parse a timestamp into a map containing the suffix, the timestamp
   itself, and the raw string value from slack's API."
  [ts]
  (let [[time suffix] (.split ts "\\.")]
    {:raw_ts ts
     :ts (Timestamp. (* (Long. time) 1000))
     :ts_suffix (Long. suffix)}))

(defn insert-messages
  "Insert all messages for all channels."
  [db messages]
  (doseq [[channel messages] messages
          :let [messages (for [message messages]
                           (merge (parse-ts (:ts message))
                                  {:id (:user message)
                                   :channel channel
                                   :text (:text message)}))]]
    (println "Inserting" (count messages) "messages for" channel "...")
    (when (seq messages)
      (apply j/insert! db :messages messages))))

(defn get-times
  "Get a map of channel ids to the first result of a query.
   This is useful for getting latest and oldest messages."
  [db channels query]
  (into {}
        (for [channel channels]
          [channel (-> (j/query db [query channel])
                       (first)
                       (:raw_ts))])))

(defn latest
  "Get the latest messages for channels."
  [db channels]
  (get-times db channels
             "select raw_ts from messages
                where channel = ?
                order by ts desc
                limit 1;"))

(defn oldest
  "Get the oldest messages for channels."
  [db channels]
  (get-times db channels
             "select raw_ts from messages
                where channel = ?
                order by ts asc
                limit 1;"))

(defn backup-chats
  "Backup ims, groups, or channels."
  [db token kind]
  (let [channels (map second (list-chats token kind))
        recent (latest db channels)
        messages (messages token recent kind)]
    (insert-messages db messages)))

(defn update-nouns
  "Update all users or channels."
  [db to-update nouns kind] 
  (println "Updating" (count to-update) (name kind))
  (doseq [noun to-update
          :let [name (get-in nouns [noun :name])]
          :when (not= name (get-in nouns [noun :name]))]
    (j/update! db kind {:id noun} ["id = ?" noun])))

(defn insert-nouns
  "Insert new channels or users."
  [db to-insert nouns kind]
  (println "Inserting" (count to-insert) "new" (name kind))
  (apply j/insert! db kind
         (for [noun to-insert]
           (select-keys (nouns noun) [:id :name]))))

(defn delete-nouns
  "Delete channels or users that do not exist anymore."
  [db to-delete nouns kind]
  (println "Deleting" (count to-delete) (name kind))
  (doseq [noun to-delete]
    (j/delete! db kind ["id = ?" noun])))

(defn map-ids
  "Map of ids to relevant records."
  [data]
  (into {} (map (juxt :id identity) data)))

(defn backup-nouns
  "Backup users or channels."
  [db token kind]
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

(defn backup
  "Backup ALL the things. kinds can be any combination of
   the strings channels, groups, ims, channel-ids, and
   user-ids."
  [db token kinds]
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
