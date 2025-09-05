(ns qq.monkeyq.tracker
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [qq.roam.client :as roam]))

(def snapshot-file "monkeyq-snapshots.json")

(defn get-current-monkeyq-content []
  "Get current MonkeyQ content from yuanvv graph - both page content and references"
  (let [query "[:find ?uid ?string :in $ ?page-title :where [?b :block/uid ?uid] [?b :block/string ?string] [?b :block/refs ?page] [?page :node/title ?page-title]]"
        result (roam/roam-query :yuanvv query ["MonkeyQ"])]
    (if (:error result)
      {:error (:error result)}
      {:content (map (fn [[uid string]] {:uid uid :string string}) (:result result))
       :timestamp (System/currentTimeMillis)})))

(defn save-snapshot [content]
  "Save current content as snapshot"
  (let [snapshot {:timestamp (System/currentTimeMillis)
                  :content content}]
    (spit snapshot-file (json/write-str snapshot))
    snapshot))

(defn load-last-snapshot []
  "Load the last saved snapshot"
  (if (.exists (io/file snapshot-file))
    (json/read-str (slurp snapshot-file) :key-fn keyword)
    nil))

(defn extract-blocks [content]
  "Extract all blocks - content is already in the right format"
  (if (sequential? content)
    content  ; Content is already a list of {:uid :string} maps
    []))

(defn find-new-blocks [current-blocks last-blocks]
  "Find blocks that exist in current but not in last"
  (let [last-uids (set (map :uid last-blocks))]
    (filter #(not (contains? last-uids (:uid %))) current-blocks)))

(defn track-changes []
  "Track changes since last snapshot"
  (let [current (get-current-monkeyq-content)]
    (if (:error current)
      (println "❌ Error getting current content:" (:error current))
      (let [current-blocks (extract-blocks (:content current))
            last-snapshot (load-last-snapshot)
            last-blocks (if last-snapshot 
                         (extract-blocks (:content last-snapshot))
                         [])
            new-blocks (find-new-blocks current-blocks last-blocks)]
        
        (println "📊 MonkeyQ Change Summary:")
        (println "  Current blocks:" (count current-blocks))
        (println "  Last snapshot:" (if last-snapshot 
                                     (java.util.Date. (:timestamp last-snapshot))
                                     "None"))
        (println "  New blocks:" (count new-blocks))
        
        (when (seq new-blocks)
          (println "\n🆕 New blocks since last check:")
          (doseq [block new-blocks]
            (println "  •" (subs (:string block) 0 (min 100 (count (:string block)))) "...")))
        
        ;; Save current as new snapshot
        (save-snapshot (:content current))
        (println "\n✅ Snapshot updated")
        
        {:new-blocks new-blocks :total-new (count new-blocks)}))))

(defn show-new-blocks []
  "Show only new blocks without updating snapshot"
  (let [current (get-current-monkeyq-content)]
    (if (:error current)
      (println "❌ Error getting current content:" (:error current))
      (let [current-blocks (extract-blocks (:content current))
            last-snapshot (load-last-snapshot)
            last-blocks (if last-snapshot 
                         (extract-blocks (:content last-snapshot))
                         [])
            new-blocks (find-new-blocks current-blocks last-blocks)]
        
        (if (seq new-blocks)
          (do
            (println "🆕 New MonkeyQ blocks (" (count new-blocks) " total):")
            (doseq [block new-blocks]
              (println "\n" (:string block))))
          (println "📋 No new MonkeyQ blocks since last check"))
        
        new-blocks))))
