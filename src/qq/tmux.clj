(ns qq.tmux
  "Tmux integration with incremental output caching"
  (:require [clojure.string :as str]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [qq.timeline :as timeline]))

;; Configuration
(def ^:private QQ-DIR (str (System/getProperty "user.home") "/.knock/qq"))
(def ^:private SESSION-PREFIX "qq-")

;; Utility Functions

(defn session-name [terse-name]
  "Generate tmux session name from terse name"
  (str SESSION-PREFIX terse-name))

(defn- session-log-file [session-id]
  "Get log file path for session"
  (str QQ-DIR "/sessions/" session-id "/tmux.log"))

(defn- ensure-session-dir [session-id]
  "Ensure session directory exists"
  (let [session-dir (str QQ-DIR "/sessions/" session-id)]
    (.mkdirs (io/file session-dir))))

;; Tmux Operations

(defn create-session [session-id]
  "Create a new tmux session for Amazon Q"
  (ensure-session-dir session-id)
  (let [tmux-name (session-name session-id)]
    (try
      ;; Create detached tmux session
      (p/shell "tmux" "new-session" "-d" "-s" tmux-name)
      ;; Start Amazon Q in the session
      (p/shell "tmux" "send-keys" "-t" tmux-name "q chat" "Enter")
      ;; Wait a moment for Q to start
      (Thread/sleep 2000)
      {:success true :tmux-name tmux-name}
      (catch Exception e
        (println "Error creating tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))

(defn capture-output [session-id]
  "Capture current tmux session output"
  (let [tmux-name (session-name session-id)]
    (try
      (let [result (p/shell {:out :string :continue true} 
                           "tmux" "capture-pane" "-t" tmux-name "-p")]
        (if (zero? (:exit result))
          (:out result)
          (do
            (println "Warning: Could not capture tmux output for session" session-id)
            "")))
      (catch Exception e
        (println "Error capturing tmux output:" (.getMessage e))
        ""))))

(defn send-keys [session-id text]
  "Send keys to tmux session"
  (let [tmux-name (session-name session-id)]
    (try
      (p/shell "tmux" "send-keys" "-t" tmux-name text "Enter")
      {:success true}
      (catch Exception e
        (println "Error sending keys to tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))

(defn- detect-error-output [output]
  "Detect if output contains Q errors"
  (or (str/includes? output "\u001b[31m")  ; Red ANSI color
      (str/includes? output "Error:")
      (str/includes? output "ERROR:")
      (str/includes? output "Failed:")))

(defn- auto-continue-on-error [session-id]
  "Send 'continue' command if Q is in error state"
  (println "🔄 Detected Q error, sending 'continue' to recover...")
  (send-keys session-id "continue")
  (Thread/sleep 1000))

(defn send-and-wait [session-id question]
  "Send question and wait for response (synchronous) with timeline logging"
  (let [tmux-name (session-name session-id)
        start-time (System/currentTimeMillis)]
    ;; Log the question to timeline
    (timeline/log-question session-id question)
    
    ;; Capture initial state
    (let [initial-output (capture-output session-id)]
      ;; Send question
      (send-keys session-id question)
      
      ;; Wait for response with polling
      (loop [attempts 0
             max-attempts 60]  ; 5 minutes timeout
        (Thread/sleep 5000)  ; Wait 5 seconds between checks
        (let [current-output (capture-output session-id)
              new-content (if (str/includes? current-output initial-output)
                           (str/replace-first current-output initial-output "")
                           current-output)]
          
          (cond
            ;; Got new content
            (and (not (str/blank? new-content))
                 (not= new-content initial-output))
            (let [response-time (- (System/currentTimeMillis) start-time)
                  final-response (if (detect-error-output new-content)
                                  (do
                                    (auto-continue-on-error session-id)
                                    ;; Wait for recovery and get new output
                                    (Thread/sleep 3000)
                                    (let [recovered-output (capture-output session-id)]
                                      (if (detect-error-output recovered-output)
                                        new-content  ; Return error if recovery failed
                                        recovered-output)))  ; Return recovered output
                                  new-content)]
              ;; Log the answer to timeline
              (timeline/log-answer session-id final-response response-time)
              final-response)
            
            ;; Timeout
            (>= attempts max-attempts)
            (let [timeout-response "⏰ Timeout: No response from Amazon Q"
                  response-time (- (System/currentTimeMillis) start-time)]
              (println "⏰ Timeout waiting for Q response")
              ;; Log timeout as answer
              (timeline/log-answer session-id timeout-response response-time)
              timeout-response)
            
            ;; Continue waiting
            :else
            (recur (inc attempts) max-attempts)))))))

(defn send-async [session-id question]
  "Send question asynchronously with timeline logging, returns a promise"
  (let [promise (promise)]
    (future
      (try
        (let [response (send-and-wait session-id question)]
          ;; Timeline logging is handled in send-and-wait
          (deliver promise {:success true :response response}))
        (catch Exception e
          (deliver promise {:success false :error (.getMessage e)}))))
    promise))

(defn session-exists? [session-id]
  "Check if tmux session exists"
  (let [tmux-name (session-name session-id)]
    (try
      (let [result (p/shell {:continue true} "tmux" "has-session" "-t" tmux-name)]
        (zero? (:exit result)))
      (catch Exception e
        false))))

(defn kill-session [session-id]
  "Kill tmux session"
  (let [tmux-name (session-name session-id)]
    (try
      (p/shell "tmux" "kill-session" "-t" tmux-name)
      {:success true}
      (catch Exception e
        (println "Error killing tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))
