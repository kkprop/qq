(ns qq.monkeyq.checkpoints
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [qq.roam.client :as roam]
            [qq.tui :as tui])
  (:import [java.time Instant ZoneId]
           [java.time.format DateTimeFormatter]))

(def checkpoints-dir "monkeyq-checkpoints")

(defn timestamp-to-dirname [timestamp-ms]
  "Convert timestamp to directory name: 2025-09-05_16-30-00"
  (let [instant (Instant/ofEpochMilli timestamp-ms)
        zoned (.atZone instant (ZoneId/of "Asia/Shanghai"))
        formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")]
    (.format zoned formatter)))

(defn create-checkpoint-dir [timestamp-ms]
  "Create checkpoint directory for timestamp"
  (let [dirname (timestamp-to-dirname timestamp-ms)
        checkpoint-path (str checkpoints-dir "/" dirname)]
    (.mkdirs (io/file checkpoint-path))
    checkpoint-path))

(defn get-blocks-in-timerange [start-ms end-ms]
  "Get MonkeyQ blocks created/edited in time range with parent context"
  (let [query "[:find ?uid ?string ?create-time ?edit-time :in $ ?start-time ?end-time :where [?b :block/uid ?uid] [?b :block/string ?string] [?b :create/time ?create-time] [?b :edit/time ?edit-time] [?b :block/refs ?page] [?page :node/title \"MonkeyQ\"] [(>= ?create-time ?start-time)] [(<= ?create-time ?end-time)]]"
        result (roam/roam-query :yuanvv query [start-ms end-ms])]
    (if (:error result)
      {:error (:error result)}
      {:blocks (map (fn [[uid string create-time edit-time]]
                     {:uid uid 
                      :string string 
                      :create-time create-time 
                      :edit-time edit-time}) 
                   (:result result))})))

(defn get-block-with-context [block-uid]
  "Get block with parent and children context"
  (let [parent-query "[:find ?parent-uid ?parent-string :in $ ?block-uid :where [?b :block/uid ?block-uid] [?parent :block/children ?b] [?parent :block/uid ?parent-uid] [?parent :block/string ?parent-string]]"
        children-query "[:find ?child-uid ?child-string :in $ ?block-uid :where [?b :block/uid ?block-uid] [?b :block/children ?child] [?child :block/uid ?child-uid] [?child :block/string ?child-string]]"
        parent-result (roam/roam-query :yuanvv parent-query [block-uid])
        children-result (roam/roam-query :yuanvv children-query [block-uid])]
    
    {:parent (when-let [[parent-uid parent-string] (first (:result parent-result))]
               {:uid parent-uid :string parent-string})
     :children (map (fn [[child-uid child-string]]
                     {:uid child-uid :string child-string})
                   (:result children-result))}))

(defn save-enhanced-checkpoint [start-ms end-ms]
  "Save checkpoint with context and response tracking"
  (let [checkpoint-path (create-checkpoint-dir end-ms)
        blocks-result (get-blocks-in-timerange start-ms end-ms)]
    
    (if (:error blocks-result)
      (println "❌ Error getting blocks:" (:error blocks-result))
      (let [blocks (:blocks blocks-result)
            enriched-blocks (map (fn [block]
                                  (let [context (get-block-with-context (:uid block))]
                                    (assoc block 
                                           :parent (:parent context)
                                           :children (:children context)
                                           :response-status :pending
                                           :response-uid nil
                                           :response-date nil)))
                                blocks)]
        
        ;; Save metadata
        (spit (str checkpoint-path "/metadata.json")
              (json/write-str {:start-time start-ms
                              :end-time end-ms
                              :start-readable (timestamp-to-dirname start-ms)
                              :end-readable (timestamp-to-dirname end-ms)
                              :block-count (count enriched-blocks)
                              :pending-responses (count (filter #(= (:response-status %) :pending) enriched-blocks))
                              :created-at (System/currentTimeMillis)}))
        
        ;; Save each block with full context
        (doseq [[i block] (map-indexed vector enriched-blocks)]
          (spit (str checkpoint-path "/block-" (format "%03d" i) "-" (:uid block) ".json")
                (json/write-str block :key-fn name)))
        
        (println "✅ Enhanced checkpoint saved:" checkpoint-path)
        (println "📊 Blocks saved:" (count enriched-blocks))
        (println "📋 Pending responses:" (count (filter #(= (:response-status %) :pending) enriched-blocks)))
        
        {:checkpoint-path checkpoint-path 
         :blocks enriched-blocks 
         :count (count enriched-blocks)}))))

(defn show-pending-feedback []
  "Show all pending VV feedback across checkpoints"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort))]
        (println "📋 Pending VV Feedback:")
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (and (= (:response-status block) :pending)
                          (or (str/includes? (:string block) "#VVFeedback")
                              (str/includes? (:string block) "#VVRequest")))
                  (println "\n🔸" (:uid block) "(" dir ")")
                  (println "  Content:" (subs (:string block) 0 (min 100 (count (:string block)))) "...")
                  (when (:parent block)
                    (println "  Parent:" (subs (:parent-string (:parent block)) 0 (min 80 (count (:parent-string (:parent block))))) "..."))))))))
      (println "📭 No checkpoints found"))))

(defn mark-no-response-needed [block-uid]
  "Mark a block as not needing a response"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %)))]
        (loop [dirs dirs
               found false]
          (if (or found (empty? dirs))
            (if found
              (println "✅ Marked" block-uid "as no response needed")
              (println "❌ Block" block-uid "not found"))
            (let [dir (first dirs)
                  checkpoint-path (str checkpoints-dir "/" dir)
                  block-files (->> (io/file checkpoint-path)
                                  .listFiles
                                  (filter #(str/includes? (.getName %) block-uid)))]
              (if (seq block-files)
                (let [block-file (first block-files)
                      block (json/read-str (slurp block-file) :key-fn keyword)
                      updated-block (assoc block :response-status "no-response-needed")]
                  (spit block-file (json/write-str updated-block :indent true))
                  (recur (rest dirs) true))
                (recur (rest dirs) false))))))
      (println "📭 No checkpoints found"))))

(defn mark-responded [block-uid response-uid]
  "Mark a block as responded with our response UID"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (when (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %)))]
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (= (:uid block) block-uid)
                  (let [updated-block (assoc block 
                                            :response-status :responded
                                            :response-uid response-uid
                                            :response-date (timestamp-to-dirname (System/currentTimeMillis)))]
                    (spit (.getPath block-file) (json/write-str updated-block :key-fn name))
                    (println "✅ Marked" block-uid "as responded with" response-uid)))))))))))

(defn find-local-response-file [response-uid vv-feedback]
  "Find local markdown file for this response"
  (let [drafts-dir "drafts/vv-responses/"
        files (when (.exists (io/file drafts-dir))
                (->> (io/file drafts-dir)
                     .listFiles
                     (filter #(str/ends-with? (.getName %) ".md"))))]
    (when files
      ;; Try to match by content or filename pattern
      (first (filter (fn [file]
                      (let [content (slurp file)]
                        (or (str/includes? content response-uid)
                            (str/includes? content (subs vv-feedback 0 (min 50 (count vv-feedback))))
                            (str/includes? (.getName file) "vv-"))))
                    files)))))

(defn show-response-details []
  "Show detailed information about all responses with TUI selection"
  (let [checkpoints-root (io/file checkpoints-dir)
        responses (atom [])]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort))]
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (and (or (str/includes? (:string block) "#VVFeedback")
                              (str/includes? (:string block) "#VVRequest"))
                          (= (get block :response-status) "responded"))
                  (let [local-file (find-local-response-file (:response-uid block) (:string block))]
                    (swap! responses conj {:uid (:uid block)
                                          :feedback (:string block)
                                          :response-uid (:response-uid block)
                                          :response-date (:response-date block)
                                          :context (get (:parent block) :parent-string "")
                                          :local-file local-file
                                          :dir dir})))))))
        
        (if (empty? @responses)
          (println "📭 No responses found")
          (let [render-fn (fn [state]
                           (let [items (:items state)
                                 selected (:selected state)
                                 query (:query state)]
                             (str "🎯 Select response to view details:\n"
                                  "===================================\n"
                                  "🔍 Filter: " query (if (:cursor-visible state) "█" " ") "\n"
                                  (str/join "\n"
                                           (map-indexed
                                            (fn [i response]
                                              (let [prefix (if (= i selected) "► " "  ")
                                                    feedback-preview (subs (:feedback response) 0 (min 60 (count (:feedback response))))
                                                    file-info (if (:local-file response) 
                                                               (str " [" (.getName (:local-file response)) "]")
                                                               " [no file]")]
                                                (str prefix (inc i) ". " (:uid response) " - " feedback-preview "..." file-info)))
                                            items))
                                  "\n\n↑↓/Ctrl+P/Ctrl+N/Ctrl+K Navigate | Esc Clear | Enter Select | Backspace Delete | Ctrl+C Quit")))
                selected (tui/select-from @responses render-fn)]
            (when selected
              (println "\n📄 Response Details for" (:uid selected) ":")
              (println "=" (apply str (repeat 60 "=")))
              (println "📋 VV Feedback:" (:feedback selected))
              (println "🎯 Response UID:" (:response-uid selected))
              (println "📅 Response Date:" (:response-date selected))
              (when (not (str/blank? (:context selected)))
                (println "🔗 Context:" (:context selected)))
              (if (:local-file selected)
                (do
                  (println "📁 Local File:" (.getPath (:local-file selected)))
                  (println "\n📝 Content:")
                  (println (slurp (:local-file selected))))
                (println "❌ No local file found for this response"))))))
      (println "📭 No checkpoints found"))))

(defn response-status-overview []
  "Show overview of all VV feedback response status"
  (let [checkpoints-root (io/file checkpoints-dir)
        stats (atom {:total 0 :pending 0 :responded 0 :no-response-needed 0})]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %)))]
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (or (str/includes? (:string block) "#VVFeedback")
                         (str/includes? (:string block) "#VVRequest"))
                  (swap! stats update :total inc)
                  (let [status (get block :response-status :pending)]
                    (if (contains? @stats status)
                      (swap! stats update status inc)
                      (do
                        (swap! stats assoc status 0)
                        (swap! stats update status inc)))))))))
        
        (println "📊 VV Feedback Response Status:")
        (println "  Total VV feedback:" (:total @stats))
        (println "  📋 Pending:" (:pending @stats))
        (println "  ✅ Responded:" (:responded @stats))
        (println "  ➖ No response needed:" (:no-response-needed @stats)))
      (println "📭 No checkpoints found"))))

(defn show-pending-details []
  "Show detailed information about pending feedback with full context"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort))]
        (println "📋 Pending VV Feedback Details:")
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (and (or (str/includes? (:string block) "#VVFeedback")
                              (str/includes? (:string block) "#VVRequest"))
                          (= (get block :response-status :pending) :pending))
                  (println "\n🔸" (:uid block) "(" dir ")")
                  (println "  VV Feedback:" (:string block))
                  (when (:parent block)
                    (println "  Parent Context:" (:parent-string (:parent block))))
                  (when (seq (:children block))
                    (println "  Children:")
                    (doseq [child (:children block)]
                      (println "    -" (:string child))))))))))
      (println "📭 No checkpoints found"))))

(defn save-checkpoint [start-ms end-ms]
  "Save checkpoint with blocks changed in time range (legacy wrapper)"
  (save-enhanced-checkpoint start-ms end-ms))

(defn list-checkpoints []
  "List all saved checkpoints"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort)
                     (reverse))] ; Latest first
        (println "📋 Available checkpoints:")
        (doseq [dir dirs]
          (let [metadata-file (str checkpoints-dir "/" dir "/metadata.json")]
            (if (.exists (io/file metadata-file))
              (let [metadata (json/read-str (slurp metadata-file) :key-fn keyword)]
                (println "  •" dir "(" (:block-count metadata) "blocks)"))
              (println "  •" dir "(no metadata)"))))
        dirs)
      (do
        (println "📭 No checkpoints found")
        []))))

(defn parse-fuzzy-time [fuzzy-input]
  "Parse fuzzy time input to timestamp
   Examples: 'yesterday 18:00', '2025-09-04 18:00', 'today 10:00', '3 hours ago'"
  (let [now (System/currentTimeMillis)
        today-start (- now (mod now 86400000)) ; Start of today in ms
        input (str/lower-case (str/trim fuzzy-input))]
    
    (cond
      ;; Specific date and time: "2025-09-04 18:00"
      (re-matches #"\d{4}-\d{2}-\d{2} \d{1,2}:\d{2}" input)
      (let [[date time] (str/split input #" ")
            [year month day] (str/split date #"-")
            [hour minute] (str/split time #":")]
        (+ (* (Long/parseLong year) 31536000000)  ; Rough calculation
           (* (Long/parseLong month) 2592000000)
           (* (Long/parseLong day) 86400000)
           (* (Long/parseLong hour) 3600000)
           (* (Long/parseLong minute) 60000)))
      
      ;; Today with time: "today 10:00"
      (str/starts-with? input "today ")
      (let [time-part (str/replace input "today " "")
            [hour minute] (str/split time-part #":")]
        (+ today-start
           (* (Long/parseLong hour) 3600000)
           (* (Long/parseLong minute) 60000)))
      
      ;; Yesterday with time: "yesterday 18:00"
      (str/starts-with? input "yesterday ")
      (let [time-part (str/replace input "yesterday " "")
            [hour minute] (str/split time-part #":")]
        (+ today-start
           -86400000  ; -1 day
           (* (Long/parseLong hour) 3600000)
           (* (Long/parseLong minute) 60000)))
      
      ;; Hours ago: "3 hours ago"
      (re-matches #"\d+ hours? ago" input)
      (let [hours (Long/parseLong (re-find #"\d+" input))]
        (- now (* hours 3600000)))
      
      ;; Default: treat as current time
      :else now)))

(defn create-checkpoint-fuzzy [start-fuzzy end-fuzzy]
  "Create checkpoint with fuzzy time inputs"
  (let [start-ms (parse-fuzzy-time start-fuzzy)
        end-ms (parse-fuzzy-time end-fuzzy)]
    
    (println "🎯 Creating checkpoint...")
    (println "📅 Start:" start-fuzzy "→" (timestamp-to-dirname start-ms))
    (println "📅 End:" end-fuzzy "→" (timestamp-to-dirname end-ms))
    
    (save-checkpoint start-ms end-ms)))

(defn get-last-checkpoint-time []
  "Get the timestamp of the most recent checkpoint"
  (let [checkpoints-root (io/file checkpoints-dir)]
    (when (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort)
                     (last))] ; Most recent
        (when dirs
          ;; Convert "2025-09-05_17-59-48" to "2025-09-05 17:59:48"
          (let [timestamp-str (-> dirs
                                 (str/replace #"_" " ")
                                 (str/replace #"-" ":")
                                 (str/replace #"-" ":"))] ; Replace hyphens with colons
            timestamp-str))))))

(defn create-checkpoint-cli [& args]
  "CLI interface for creating checkpoints with fuzzy time"
  (cond
    (= (count args) 2)
    (let [[start-fuzzy end-fuzzy] args]
      (create-checkpoint-fuzzy start-fuzzy end-fuzzy))
    
    (= (count args) 1)
    (let [end-fuzzy (first args)
          last-checkpoint-time (get-last-checkpoint-time)
          start-fuzzy (or last-checkpoint-time "24 hours ago")]
      (println "📋 Using start time:" start-fuzzy)
      (create-checkpoint-fuzzy start-fuzzy end-fuzzy))
    
    :else
    (do
      (println "📋 Usage examples:")
      (println "  bb checkpoint 'yesterday 18:00' 'today 10:00'")
      (println "  bb checkpoint '2025-09-04 18:00' '2025-09-05 10:00'")
      (println "  bb checkpoint 'today 10:00'  # (starts from 24 hours ago)")
      (println "  bb checkpoint '3 hours ago' 'now'"))))
