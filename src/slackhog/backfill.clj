(ns slackhog.backfill
  "Utilities for patching in data from a full export."
  (:require [slackhog.slack :as s]
            [cheshire.core :refer [parse-string]]
            [me.raynes.fs :as fs]))

(defn ts>
  "Is the first timestamp greater than the other?"
  [ts other-ts]
  (let [[ts] (.split ts "\\.")
        [other-ts] (.split other-ts "\\.")]
    (> (Long. ts) (Long. other-ts))))

(defn messages
  "Returns a map of channel ids to the messages from that channel."
  [token path]
  (into {}
        (let [channels (s/list-chats token "channels")]
          (for [f (-> path fs/expand-home fs/list-dir)
                :when (fs/directory? f)]
            [(get channels (fs/base-name f))
             (mapcat (comp #(parse-string % keyword) slurp)
                     (sort (fs/list-dir f)))]))))

(defn prune
  "Prune a channel's messages however you like. Specify
   :oldest and/or :latest timestamps to narrow down which
   messages we want."
  [channel messages & {:keys [latest oldest]}]
  (let [older (if oldest
                (drop-while #(ts> oldest (:ts %)) messages)
                messages)
        newer (if latest
                (take-while #(ts> latest (:ts %)) older)
                older)]
    (s/only-messages newer)))

(defn backfill
  "Determine the data we have for messages and backfill
   from a full export as necessary."
  [db messages]
  (let [times (s/oldest db (map key messages))
        messages (for [[channel messages] messages]
                   [channel (prune channel messages
                                   :latest (times channel))])]
    (s/insert-messages db messages)))
