(ns qq.log
  "Simple timestamped logging utility for QQ"
  (:require [clojure.java.io :as io]))

(defn timestamp []
  "Get current timestamp in readable format"
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))

(defn log [& args]
  "Log message with timestamp to both console and watcher.log"
  (let [msg (str "[" (timestamp) "] " (apply str args))
        log-file "./watcher.log"]
    ;; Print to console
    (println msg)
    ;; Append to log file
    (try
      (io/make-parents log-file)
      (spit log-file (str msg "\n") :append true)
      (catch Exception e
        (println "[ERROR] Failed to write to log file:" (.getMessage e))))))

(defn log-info [& args]
  "Log info message"
  (apply log "ℹ️ " args))

(defn log-error [& args]
  "Log error message"
  (apply log "❌ " args))

(defn log-success [& args]
  "Log success message"
  (apply log "✅ " args))

(defn log-debug [& args]
  "Log debug message"
  (apply log "🔍 " args))
