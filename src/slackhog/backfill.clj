(ns slackhog.backfill
  "Utilities for patching in data from a full export."
  (:require [slackhog.slack :as s]
            [cheshire.core :refer [parse-string]]
            [me.raynes.fs :as fs]))

(defn ts> [ts other-ts]
  (let [[ts] (.split ts "\\.")
        [other-ts] (.split other-ts "\\.")]
    (> (Long. ts) (Long. other-ts))))

(defn messages [token path]
  (into {}
        (let [channels (s/list-chats token "channels")]
          (for [f (-> path fs/expand-home fs/list-dir)
                :when (fs/directory? f)]
            [(get channels (fs/base-name f))
             (mapcat (comp #(parse-string % keyword) slurp)
                     (sort (fs/list-dir f)))]))))

(defn prune [channel messages & {:keys [latest oldest]}]
  (let [older (if oldest
                (drop-while #(ts> oldest (:ts %)) messages)
                messages)
        newer (if latest
                (take-while #(ts> latest (:ts %)) older)
                older)]
    (s/only-messages newer)))

(defn backfill [db messages]
  (let [times (s/oldest db (map key messages))
        messages (for [[channel messages] messages]
                   [channel (prune channel messages
                                   :latest (times channel))])]
    (s/insert-messages db messages)))
