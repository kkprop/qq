(ns qq.monitor
  "Tmux window monitoring and activity tracking"
  (:require [clojure.string :as str]
            [babashka.process :as p]
            [clojure.data.json :as json]))

;; ============================================================================
;; BASIC WINDOW DISCOVERY
;; ============================================================================

(defn list-tmux-sessions []
  "Get all tmux sessions with detailed information"
  (try
    (let [result (p/shell {:out :string :err :string} "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_created}|#{session_last_attached}'")]
      (if (zero? (:exit result))
        (->> (str/split-lines (:out result))
             (remove str/blank?)
             (map (fn [line]
                    (let [[name windows created last-attached] (str/split line #"\|")]
                      {:session-name name
                       :window-count (Integer/parseInt windows)
                       :created created
                       :last-attached last-attached}))))
        []))
    (catch Exception e
      (println (str "❌ Error listing tmux sessions: " (.getMessage e)))
      [])))

(defn list-tmux-windows [session-name]
  "Get all windows in a specific tmux session"
  (try
    (let [result (p/shell {:out :string :err :string} 
                         "tmux" "list-windows" "-t" session-name 
                         "-F" "#{window_index}|#{window_name}|#{window_active}|#{window_panes}|#{window_layout}")]
      (if (zero? (:exit result))
        (->> (str/split-lines (:out result))
             (remove str/blank?)
             (map (fn [line]
                    (let [[index name active panes layout] (str/split line #"\|")]
                      {:window-index (Integer/parseInt index)
                       :window-name name
                       :active? (= active "1")
                       :pane-count (Integer/parseInt panes)
                       :layout layout
                       :session-name session-name}))))
        []))
    (catch Exception e
      (println (str "❌ Error listing windows for session " session-name ": " (.getMessage e)))
      [])))

(defn get-all-windows []
  "Get all windows across all tmux sessions"
  (let [sessions (list-tmux-sessions)]
    (mapcat (fn [session]
              (list-tmux-windows (:session-name session)))
            sessions)))

;; ============================================================================
;; WINDOW ACTIVITY MONITORING
;; ============================================================================

(defn get-window-activity [session-name window-index]
  "Get activity information for a specific window"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "display-message" "-t" (str session-name ":" window-index)
                         "-p" "#{window_activity}|#{window_silence}|#{window_last_flag}")]
      (if (zero? (:exit result))
        (let [[activity silence last-flag] (str/split (str/trim (:out result)) #"\|")]
          {:activity activity
           :silence silence
           :last-flag last-flag
           :timestamp (System/currentTimeMillis)})
        nil))
    (catch Exception e
      (println (str "❌ Error getting activity for " session-name ":" window-index ": " (.getMessage e)))
      nil)))

(defn capture-window-content [session-name window-index]
  "Capture the current content of a tmux window"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "capture-pane" "-t" (str session-name ":" window-index) "-p")]
      (if (zero? (:exit result))
        {:content (:out result)
         :timestamp (System/currentTimeMillis)
         :session-name session-name
         :window-index window-index}
        nil))
    (catch Exception e
      (println (str "❌ Error capturing content for " session-name ":" window-index ": " (.getMessage e)))
      nil)))

;; ============================================================================
;; MONITORING STATE MANAGEMENT
;; ============================================================================

(def monitoring-state (atom {:windows {}
                            :active false
                            :last-scan 0}))

(defn update-window-state [window-info]
  "Update the monitoring state for a window"
  (let [window-key (str (:session-name window-info) ":" (:window-index window-info))]
    (swap! monitoring-state 
           assoc-in [:windows window-key] 
           (merge window-info {:last-updated (System/currentTimeMillis)}))))

(defn scan-all-windows []
  "Scan all tmux windows and update monitoring state"
  (let [windows (get-all-windows)]
    (doseq [window windows]
      (let [activity (get-window-activity (:session-name window) (:window-index window))
            enhanced-window (merge window activity)]
        (update-window-state enhanced-window)))
    (swap! monitoring-state assoc :last-scan (System/currentTimeMillis))
    (count windows)))

;; ============================================================================
;; MONITORING QUERIES
;; ============================================================================

(defn get-active-windows []
  "Get all currently active tmux windows"
  (filter :active? (get-all-windows)))

(defn get-q-sessions []
  "Get all Q-related tmux sessions"
  (filter #(str/includes? (:session-name %) "qq") (list-tmux-sessions)))

(defn get-window-by-name [window-name]
  "Find windows by name pattern"
  (filter #(str/includes? (:window-name %) window-name) (get-all-windows)))

(defn get-monitoring-summary []
  "Get a summary of current monitoring state"
  (let [state @monitoring-state
        windows (:windows state)
        active-count (count (filter :active? (vals windows)))
        total-count (count windows)]
    {:total-windows total-count
     :active-windows active-count
     :monitoring-active (:active state)
     :last-scan (:last-scan state)
     :q-sessions (count (get-q-sessions))}))

;; ============================================================================
;; DISPLAY FUNCTIONS
;; ============================================================================

(defn display-window-info [window]
  "Display formatted information about a window"
  (println (str "📺 " (:session-name window) ":" (:window-index window) 
                " [" (:window-name window) "]"
                (if (:active? window) " 🟢 ACTIVE" " ⚪ INACTIVE")
                " (" (:pane-count window) " panes)")))

(defn display-all-windows []
  "Display information about all tmux windows"
  (println "🖥️  TMUX WINDOW MONITORING")
  (println "========================")
  (let [windows (get-all-windows)]
    (if (empty? windows)
      (println "❌ No tmux sessions found")
      (do
        (doseq [window windows]
          (display-window-info window))
        (println (str "\n📊 Total: " (count windows) " windows"))))))

;; ============================================================================
;; REAL-TIME MONITORING & ACTIVITY TRACKING
;; ============================================================================

(def activity-log (atom []))

(defn log-activity [session-name window-index activity-type details]
  "Log window activity with timestamp"
  (let [entry {:timestamp (System/currentTimeMillis)
               :session-name session-name
               :window-index window-index
               :activity-type activity-type
               :details details}]
    (swap! activity-log conj entry)
    entry))

(defn detect-q-conversation-activity [content previous-content]
  "Detect Q conversation activity patterns"
  (let [current-lines (str/split-lines content)
        prev-lines (if previous-content (str/split-lines previous-content) [])
        new-lines (drop (count prev-lines) current-lines)]
    
    (cond
      ;; Q is asking for permission
      (some #(str/includes? % "Allow this action?") new-lines)
      {:type :permission-request
       :details "Q is requesting permission for tool usage"}
      
      ;; Q is using a tool
      (some #(str/includes? % "Using tool:") new-lines)
      {:type :tool-usage
       :details (first (filter #(str/includes? % "Using tool:") new-lines))}
      
      ;; Q conversation completed
      (some #(str/includes? % "✅ Response complete!") new-lines)
      {:type :conversation-complete
       :details "Q conversation finished"}
      
      ;; New Q conversation started
      (some #(str/includes? % "🚀 Question sent") new-lines)
      {:type :conversation-start
       :details "New Q conversation initiated"}
      
      ;; General activity
      (> (count new-lines) 0)
      {:type :content-change
       :details (str (count new-lines) " new lines")}
      
      :else
      nil)))

(defn monitor-window-changes [session-name window-index]
  "Monitor a specific window for changes"
  (let [window-key (str session-name ":" window-index)
        current-content (capture-window-content session-name window-index)
        previous-state (get-in @monitoring-state [:windows window-key])
        previous-content (:content previous-state)]
    
    (when current-content
      ;; Detect activity
      (let [activity (detect-q-conversation-activity 
                     (:content current-content) 
                     previous-content)]
        (when activity
          (log-activity session-name window-index (:type activity) (:details activity)))
        
        ;; Update state
        (update-window-state (merge current-content 
                                   {:window-key window-key
                                    :activity-detected (boolean activity)}))))
    
    current-content))

(defn start-monitoring [& {:keys [interval-ms q-sessions-only]
                          :or {interval-ms 5000 q-sessions-only true}}]
  "Start real-time monitoring of tmux windows"
  (swap! monitoring-state assoc :active true)
  (println "🔄 Starting tmux window monitoring...")
  (println (str "   Interval: " interval-ms "ms"))
  (println (str "   Q sessions only: " q-sessions-only))
  
  (future
    (while (:active @monitoring-state)
      (try
        (let [sessions (if q-sessions-only (get-q-sessions) (list-tmux-sessions))
              windows (mapcat #(list-tmux-windows (:session-name %)) sessions)]
          
          (doseq [window windows]
            (monitor-window-changes (:session-name window) (:window-index window)))
          
          (Thread/sleep interval-ms))
        (catch Exception e
          (println (str "❌ Monitoring error: " (.getMessage e)))
          (Thread/sleep interval-ms)))))
  
  (println "✅ Monitoring started"))

(defn stop-monitoring []
  "Stop real-time monitoring"
  (swap! monitoring-state assoc :active false)
  (println "⏹️  Monitoring stopped"))

;; ============================================================================
;; ACTIVITY ANALYSIS & REPORTING
;; ============================================================================

(defn get-recent-activity [& {:keys [minutes session-name activity-type]
                             :or {minutes 10}}]
  "Get recent activity from the activity log"
  (let [cutoff-time (- (System/currentTimeMillis) (* minutes 60 1000))
        recent-entries (filter #(> (:timestamp %) cutoff-time) @activity-log)]
    
    (cond->> recent-entries
      session-name (filter #(= (:session-name %) session-name))
      activity-type (filter #(= (:activity-type %) activity-type)))))

(defn display-recent-activity [& {:keys [minutes limit]
                                 :or {minutes 10 limit 20}}]
  "Display recent window activity"
  (println (str "📊 RECENT ACTIVITY (last " minutes " minutes)"))
  (println "=====================================")
  
  (let [activities (take limit (reverse (get-recent-activity :minutes minutes)))]
    (if (empty? activities)
      (println "❌ No recent activity")
      (doseq [activity activities]
        (let [time-str (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                               (java.util.Date. (:timestamp activity)))]
          (println (str "⏰ " time-str 
                       " 📺 " (:session-name activity) ":" (:window-index activity)
                       " 🔄 " (name (:activity-type activity))
                       " - " (:details activity))))))))

(defn get-q-conversation-summary []
  "Get summary of Q conversation activity"
  (let [q-activities (filter #(str/includes? (str (:session-name %)) "qq") @activity-log)
        by-type (group-by :activity-type q-activities)]
    
    {:total-activities (count q-activities)
     :permission-requests (count (:permission-request by-type))
     :tool-usage (count (:tool-usage by-type))
     :conversations-started (count (:conversation-start by-type))
     :conversations-completed (count (:conversation-complete by-type))
     :active-conversations (- (count (:conversation-start by-type))
                             (count (:conversation-complete by-type)))}))

(defn display-q-summary []
  "Display Q conversation activity summary"
  (let [summary (get-q-conversation-summary)]
    (println "🤖 Q CONVERSATION SUMMARY")
    (println "========================")
    (println (str "📊 Total Activities: " (:total-activities summary)))
    (println (str "🔐 Permission Requests: " (:permission-requests summary)))
    (println (str "🛠️  Tool Usage: " (:tool-usage summary)))
    (println (str "🚀 Conversations Started: " (:conversations-started summary)))
    (println (str "✅ Conversations Completed: " (:conversations-completed summary)))
    (println (str "🔄 Active Conversations: " (:active-conversations summary)))))

(defn display-monitoring-status []
  "Display current monitoring status"
  (let [summary (get-monitoring-summary)]
    (println "📊 MONITORING STATUS")
    (println "===================")
    (println (str "🖥️  Total Windows: " (:total-windows summary)))
    (println (str "🟢 Active Windows: " (:active-windows summary)))
    (println (str "🤖 Q Sessions: " (:q-sessions summary)))
    (println (str "⏰ Last Scan: " (if (> (:last-scan summary) 0)
                                    (java.util.Date. (:last-scan summary))
                                    "Never")))
    (println (str "🔄 Monitoring: " (if (:monitoring-active summary) "ON" "OFF")))))

;; ============================================================================
;; SYSTEM RESOURCE MONITORING
;; ============================================================================

(defn get-pane-pid [session-name window-index pane-index]
  "Get the PID of a specific tmux pane"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "display-message" "-t" (str session-name ":" window-index "." pane-index)
                         "-p" "#{pane_pid}")]
      (if (zero? (:exit result))
        (Integer/parseInt (str/trim (:out result)))
        nil))
    (catch Exception e
      nil)))

(defn get-process-stats [pid]
  "Get CPU and memory stats for a process"
  (try
    (let [result (p/shell {:out :string :err :string} 
                         "ps" "-p" (str pid) "-o" "pid,pcpu,pmem,rss,comm")]
      (if (zero? (:exit result))
        (let [lines (str/split-lines (:out result))
              data-line (when (> (count lines) 1) (str/trim (second lines)))]
          (when-not (str/blank? data-line)
            (let [parts (str/split data-line #"\s+")]
              (when (>= (count parts) 5)
                {:pid (Integer/parseInt (nth parts 0))
                 :cpu-percent (Double/parseDouble (nth parts 1))
                 :mem-percent (Double/parseDouble (nth parts 2))
                 :rss-kb (Integer/parseInt (nth parts 3))
                 :command (nth parts 4)}))))
        nil))
    (catch Exception e
      nil)))

(defn get-window-resource-usage [session-name window-index]
  "Get resource usage for a window"
  (when-let [main-pid (get-pane-pid session-name window-index 0)]
    (when-let [stats (get-process-stats main-pid)]
      (merge stats
             {:session-name session-name
              :window-index window-index
              :rss-mb (/ (:rss-kb stats) 1024.0)
              :timestamp (System/currentTimeMillis)}))))

(defn format-memory [mb]
  "Format memory in MB with appropriate units"
  (cond
    (>= mb 1024) (str (format "%.1f" (/ mb 1024)) " GB")
    (>= mb 1) (str (format "%.1f" mb) " MB")
    :else (str (format "%.1f" (* mb 1024)) " KB")))

(defn display-window-resources [window-stats]
  "Display resource usage for a window"
  (when window-stats
    (let [cpu-color (cond (>= (:cpu-percent window-stats) 50) "🔴"
                         (>= (:cpu-percent window-stats) 20) "🟡"
                         :else "🟢")
          mem-color (cond (>= (:mem-percent window-stats) 10) "🔴"
                         (>= (:mem-percent window-stats) 5) "🟡"
                         :else "🟢")]
      (println (str "📺 " (:session-name window-stats) ":" (:window-index window-stats)
                   " [" (:command window-stats) "]"
                   " " cpu-color " CPU: " (format "%.1f" (:cpu-percent window-stats)) "%"
                   " " mem-color " MEM: " (format "%.1f" (:mem-percent window-stats)) "% "
                   "(" (format-memory (:rss-mb window-stats)) ")")))))

(defn display-all-window-resources []
  "Display resource usage for all tmux windows"
  (println "🖥️  TMUX WINDOW RESOURCE MONITORING")
  (println "==================================")
  (let [windows (get-all-windows)]
    (if (empty? windows)
      (println "❌ No tmux sessions found")
      (let [window-stats (keep #(get-window-resource-usage (:session-name %) (:window-index %)) windows)
            sorted-stats (sort-by :cpu-percent > window-stats)]
        (if (empty? sorted-stats)
          (println "❌ Could not get resource information")
          (do
            (doseq [stats sorted-stats]
              (display-window-resources stats))
            (let [total-cpu (reduce + (map :cpu-percent sorted-stats))
                  total-mem (reduce + (map :mem-percent sorted-stats))
                  total-rss (reduce + (map :rss-mb sorted-stats))]
              (println (str "\n📊 TOTALS: CPU: " (format "%.1f" total-cpu) "% "
                           "MEM: " (format "%.1f" total-mem) "% "
                           "(" (format-memory total-rss) ")")))))))))

(defn display-q-session-resources []
  "Display resource usage for Q-related sessions only"
  (println "🤖 Q SESSION RESOURCE MONITORING")
  (println "================================")
  (let [q-sessions (get-q-sessions)]
    (if (empty? q-sessions)
      (println "❌ No Q sessions found")
      (let [q-windows (mapcat #(list-tmux-windows (:session-name %)) q-sessions)
            window-stats (keep #(get-window-resource-usage (:session-name %) (:window-index %)) q-windows)
            sorted-stats (sort-by :cpu-percent > window-stats)]
        (if (empty? sorted-stats)
          (println "❌ Could not get resource information for Q sessions")
          (do
            (doseq [stats sorted-stats]
              (display-window-resources stats))
            (let [total-cpu (reduce + (map :cpu-percent sorted-stats))
                  total-mem (reduce + (map :mem-percent sorted-stats))
                  total-rss (reduce + (map :rss-mb sorted-stats))]
              (println (str "\n🤖 Q TOTALS: CPU: " (format "%.1f" total-cpu) "% "
                           "MEM: " (format "%.1f" total-mem) "% "
                           "(" (format-memory total-rss) ")")))))))))

(defn display-top-consumers [& {:keys [limit] :or {limit 10}}]
  "Display top resource consuming windows"
  (println (str "🔥 TOP " limit " RESOURCE CONSUMERS"))
  (println "==============================")
  (let [windows (get-all-windows)
        window-stats (keep #(get-window-resource-usage (:session-name %) (:window-index %)) windows)
        ;; Sort by CPU first, then by memory if CPU is equal, then by memory size
        top-consumers (take limit 
                           (sort-by (juxt #(- (:cpu-percent %)) 
                                         #(- (:mem-percent %)) 
                                         #(- (:rss-mb %))) 
                                   window-stats))]
    (if (empty? top-consumers)
      (println "❌ No resource usage data available")
      (do
        (doseq [stats top-consumers]
          (display-window-resources stats))
        (println (str "\n📊 Sorted by: CPU% (desc) → MEM% (desc) → Memory Size (desc)"))))))

(defn display-top-consumers-detailed [& {:keys [limit] :or {limit 10}}]
  "Display top resource consuming windows with detailed sorting info"
  (println (str "🔥 TOP " limit " RESOURCE CONSUMERS (DETAILED)"))
  (println "===========================================")
  (let [windows (get-all-windows)
        window-stats (keep #(get-window-resource-usage (:session-name %) (:window-index %)) windows)
        top-consumers (take limit 
                           (sort-by (juxt #(- (:cpu-percent %)) 
                                         #(- (:mem-percent %)) 
                                         #(- (:rss-mb %))) 
                                   window-stats))]
    (if (empty? top-consumers)
      (println "❌ No resource usage data available")
      (do
        (println "Rank | Session:Window | Command | CPU% | MEM% | Memory | Sort Key")
        (println "-----|----------------|---------|------|------|--------|----------")
        (doseq [[idx stats] (map-indexed vector top-consumers)]
          (println (format "%4d | %s:%d | %s | %4.1f%% | %4.1f%% | %s | (%.1f,%.1f,%.1f)"
                          (inc idx)
                          (:session-name stats)
                          (:window-index stats)
                          (subs (:command stats) 0 (min 8 (count (:command stats))))
                          (:cpu-percent stats)
                          (:mem-percent stats)
                          (format-memory (:rss-mb stats))
                          (:cpu-percent stats)
                          (:mem-percent stats)
                          (:rss-mb stats))))
        (println (str "\n📊 Sort Key: (CPU%, MEM%, Memory MB) - all descending"))))))
