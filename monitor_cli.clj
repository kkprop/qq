#!/usr/bin/env bb

(require '[qq.monitor :as monitor])

(defn print-help []
  (println "🖥️  TMUX WINDOW MONITORING CLI")
  (println "============================")
  (println "Commands:")
  (println "  list          - List all tmux windows")
  (println "  q-sessions    - Show Q-related sessions")
  (println "  status        - Show monitoring status")
  (println "  scan          - Scan all windows once")
  (println "  monitor       - Start continuous monitoring")
  (println "  stop          - Stop monitoring")
  (println "  activity      - Show recent activity")
  (println "  q-summary     - Show Q conversation summary")
  (println "  capture <session:window> - Capture window content")
  (println "  help          - Show this help"))

(defn main [& args]
  (let [command (first args)]
    (case command
      "list"
      (monitor/display-all-windows)
      
      "q-sessions"
      (do
        (println "🤖 Q-RELATED SESSIONS:")
        (doseq [session (monitor/get-q-sessions)]
          (println (str "  📺 " (:session-name session) " (" (:window-count session) " windows)"))))
      
      "status"
      (monitor/display-monitoring-status)
      
      "scan"
      (do
        (println "🔍 Scanning all windows...")
        (let [count (monitor/scan-all-windows)]
          (println (str "✅ Scanned " count " windows"))))
      
      "monitor"
      (do
        (println "🔄 Starting continuous monitoring...")
        (monitor/start-monitoring :interval-ms 5000 :q-sessions-only true)
        (println "Press Ctrl+C to stop"))
      
      "stop"
      (monitor/stop-monitoring)
      
      "activity"
      (monitor/display-recent-activity :minutes 30 :limit 20)
      
      "q-summary"
      (monitor/display-q-summary)
      
      "capture"
      (if-let [target (second args)]
        (let [[session window] (clojure.string/split target #":")]
          (if (and session window)
            (let [content (monitor/capture-window-content session (Integer/parseInt window))]
              (if content
                (do
                  (println (str "📺 CONTENT FROM " session ":" window))
                  (println "=" (apply str (repeat 50 "=")))
                  (println (:content content)))
                (println "❌ Could not capture content")))
            (println "❌ Invalid format. Use: session:window")))
        (println "❌ Please specify session:window"))
      
      "help"
      (print-help)
      
      nil
      (print-help)
      
      (println (str "❌ Unknown command: " command "\nUse 'help' for available commands")))))

(apply main *command-line-args*)
