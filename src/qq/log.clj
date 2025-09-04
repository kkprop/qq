(ns qq.log
  "Simple timestamped logging utility for QQ"
  (:require [clojure.java.io :as io]))

(defn timestamp []
  "Get current timestamp in readable format"
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))

(defn log-to-file [log-file & args]
  "Log message with timestamp to both console and specified log file"
  (let [msg (str "[" (timestamp) "] " (apply str args))]
    ;; Print to console
    (println msg)
    ;; Append to log file
    (try
      (io/make-parents log-file)
      (spit log-file (str msg "\n") :append true)
      (catch Exception e
        (println "[ERROR] Failed to write to log file:" (.getMessage e))))))

(defn log [& args]
  "Log message with timestamp to default qq.log"
  (apply log-to-file "qq.log" args))

(defn log-info [& args]
  "Log info message to watcher.log"
  (apply log-to-file "watcher.log" "ℹ️ " args))

(defn log-error [& args]
  "Log error message to watcher.log"
  (apply log-to-file "watcher.log" "❌ " args))

(defn log-success [& args]
  "Log success message to watcher.log"
  (apply log-to-file "watcher.log" "✅ " args))

(defn log-debug [& args]
  "Log debug message to watcher.log"
  (apply log-to-file "watcher.log" "🔍 " args))
