(ns qq.context-monitor
  "Context monitoring and activity tracking for Q sessions"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [babashka.process :as p]))

;; ============================================================================
;; CONTEXT COMMAND DETECTION
;; ============================================================================

(defn detect-context-commands [content previous-content]
  "Detect various /context commands in tmux window content"
  (let [current-lines (str/split-lines (or content ""))
        prev-lines (str/split-lines (or previous-content ""))
        new-lines (drop (count prev-lines) current-lines)
        context-commands (filter #(str/starts-with? (str/trim %) "/context") new-lines)]
    
    (when (seq context-commands)
      (mapv (fn [cmd-line]
              (let [trimmed (str/trim cmd-line)
                    parts (str/split trimmed #"\s+")]
                (cond
                  (str/includes? trimmed "/context add")
                  {:type :context-add
                   :command trimmed
                   :file-path (when (> (count parts) 2) (nth parts 2))
                   :timestamp (System/currentTimeMillis)}
                  
                  (str/includes? trimmed "/context show")
                  {:type :context-show
                   :command trimmed
                   :timestamp (System/currentTimeMillis)}
                  
                  (str/includes? trimmed "/context remove")
                  {:type :context-remove
                   :command trimmed
                   :file-path (when (> (count parts) 2) (nth parts 2))
                   :timestamp (System/currentTimeMillis)}
                  
                  (str/includes? trimmed "/context clear")
                  {:type :context-clear
                   :command trimmed
                   :timestamp (System/currentTimeMillis)}
                  
                  :else
                  {:type :context-other
                   :command trimmed
                   :timestamp (System/currentTimeMillis)})))
            context-commands))))

(defn detect-context-responses [content]
  "Detect Q's responses to context commands"
  (let [lines (str/split-lines (or content ""))]
    (cond
      ;; Context add success
      (some #(str/includes? % "Added") lines)
      (let [add-line (first (filter #(str/includes? % "Added") lines))]
        (when (str/includes? add-line "path(s) to profile context")
          {:type :context-add-success
           :message add-line
           :timestamp (System/currentTimeMillis)}))
      
      ;; Context show response (token count)
      (some #(str/includes? % "Total:") lines)
      (let [total-line (first (filter #(str/includes? % "Total:") lines))
            matched-files (filter #(str/includes? % "match") lines)]
        {:type :context-show-response
         :total-tokens total-line
         :matched-files matched-files
         :timestamp (System/currentTimeMillis)})
      
      ;; Error responses
      (some #(str/includes? % "Error:") lines)
      (let [error-line (first (filter #(str/includes? % "Error:") lines))]
        {:type :context-error
         :error error-line
         :timestamp (System/currentTimeMillis)})
      
      :else nil)))

;; ============================================================================
;; TMUX INTEGRATION
;; ============================================================================

(defn capture-window-content [session-name window-index]
  "Capture content from a tmux window"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "capture-pane" "-t" (str session-name ":" window-index) "-p")]
      (if (zero? (:exit result))
        (:out result)
        nil))
    (catch Exception e
      nil)))

(defn get-q-sessions []
  "Get all Q-related tmux sessions"
  (try
    (let [result (p/shell {:out :string :err :string} 
                         "tmux list-sessions -F '#{session_name}'")]
      (if (zero? (:exit result))
        (filter #(str/includes? % "qq") (str/split-lines (:out result)))
        []))
    (catch Exception e
      [])))

;; ============================================================================
;; ACTIVITY LOGGING
;; ============================================================================

(defn get-context-log-file []
  "Get the context activity log file path"
  (let [qq-dir (str (System/getProperty "user.home") "/.knock/qq")]
    (.mkdirs (io/file qq-dir))
    (str qq-dir "/context-activity.json")))

(defn load-context-log []
  "Load context activity log"
  (let [log-file (get-context-log-file)]
    (if (.exists (io/file log-file))
      (try
        (json/read-str (slurp log-file) :key-fn keyword)
        (catch Exception e
          {:activities []}))
      {:activities []})))

(defn save-context-activity [session-name activity]
  "Save context activity to log"
  (try
    (let [log-file (get-context-log-file)
          current-log (load-context-log)
          enhanced-activity (assoc activity 
                                  :session-name session-name
                                  :window-index 1)
          updated-log (update current-log :activities conj enhanced-activity)]
      
      (spit log-file (json/write-str updated-log {:pretty true}))
      (println (str "📝 Logged context activity: " (:type activity) " in " session-name))
      enhanced-activity)
    (catch Exception e
      (println (str "❌ Error saving context activity: " (.getMessage e)))
      nil)))

;; ============================================================================
;; MONITORING FUNCTIONS
;; ============================================================================

(def window-states (atom {}))

(defn monitor-session-context [session-name]
  "Monitor context commands in a specific session"
  (let [current-content (capture-window-content session-name 1)
        previous-content (get @window-states session-name)
        session-key session-name]
    
    (when current-content
      ;; Update state
      (swap! window-states assoc session-key current-content)
      
      ;; Detect context commands
      (when-let [commands (detect-context-commands current-content previous-content)]
        (doseq [cmd commands]
          (save-context-activity session-name cmd)))
      
      ;; Detect context responses
      (when-let [response (detect-context-responses current-content)]
        (save-context-activity session-name response)))))

(defn monitor-all-q-sessions []
  "Monitor context activity across all Q sessions"
  (let [q-sessions (get-q-sessions)]
    (doseq [session q-sessions]
      (monitor-session-context session))))

;; ============================================================================
;; DISPLAY FUNCTIONS
;; ============================================================================

(defn display-context-activity []
  "Display recent context activity"
  (let [log (load-context-log)
        activities (:activities log)
        recent (take-last 10 activities)]
    
    (println "📝 RECENT CONTEXT ACTIVITY")
    (println "==========================")
    
    (if (empty? recent)
      (println "❌ No context activity recorded")
      (doseq [activity recent]
        (let [time-str (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                               (java.util.Date. (:timestamp activity)))]
          (println (str "⏰ " time-str 
                       " 📺 " (:session-name activity)
                       " 🔄 " (name (:type activity))))
          (when (:command activity)
            (println (str "   Command: " (:command activity))))
          (when (:file-path activity)
            (println (str "   File: " (:file-path activity))))
          (when (:message activity)
            (println (str "   Result: " (:message activity))))
          (when (:error activity)
            (println (str "   Error: " (:error activity))))
          (println))))))

(defn display-session-context-status [session-name]
  "Display current context status for a session"
  (println (str "📋 CONTEXT STATUS: " session-name))
  (println "================================")
  
  ;; Trigger /context show to get current status
  (println "🔍 Checking current context...")
  (let [_ (p/shell "tmux" "send-keys" "-t" session-name "/context show" "Enter")
        _ (Thread/sleep 2000)  ; Wait for response
        content (capture-window-content session-name 1)
        response (detect-context-responses content)]
    
    (if response
      (do
        (println (str "📊 " (:total-tokens response)))
        (when (:matched-files response)
          (println "📁 Matched files:")
          (doseq [file (:matched-files response)]
            (println (str "  • " file)))))
      (println "❌ Could not determine context status"))))

(defn display-all-contexts []
  "Display context information for all Q sessions"
  (println "📋 ALL SESSION CONTEXTS")
  (println "=======================")
  (let [q-sessions (get-q-sessions)]
    (if (empty? q-sessions)
      (println "❌ No Q sessions found")
      (doseq [session q-sessions]
        (display-session-context-status session)
        (println)))))

(defn watch-context-activity []
  "Continuously watch for context activity"
  (println "👁️  Watching for context activity (Ctrl+C to stop)...")
  (while true
    (monitor-all-q-sessions)
    (Thread/sleep 5000)))
