(ns qq.terminal.bridge
  "🌉 QQ Terminal-Tmux Bridge
  
  Integration layer between browser terminal and tmux Q sessions.
  Handles session lifecycle, command forwarding, and output streaming.
  
  ## Features:
  - Seamless tmux session integration
  - Real-time output streaming
  - Command history and context preservation
  - Session state management
  - Auto-creation of Q sessions
  
  ## Usage:
  ```clojure
  ;; Create or attach to Q session
  (ensure-q-session \"my-session\")
  
  ;; Send command to session
  (send-command \"my-session\" \"q chat\")
  
  ;; Get session output
  (get-session-output \"my-session\")
  ```"
  (:require [clojure.string :as str]
            [babashka.process :as p]
            [qq.session.manager :as session-mgr]))

;; Session state tracking
(def ^:private session-state (atom {}))

(defn- tmux-session-name
  "Convert session ID to tmux session name"
  [session-id]
  (str "qq-" session-id))

(defn session-exists?
  "Check if tmux session exists"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (let [result (p/shell {:out :string :continue true} "tmux" "has-session" "-t" tmux-name)]
        (= (:exit result) 0))
      (catch Exception e
        false))))

(defn create-q-session
  "Create new Q session in tmux"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (println (str "🚀 Creating Q session: " tmux-name))
      
      ;; Create tmux session
      (p/shell "tmux" "new-session" "-d" "-s" tmux-name "-c" (System/getProperty "user.dir"))
      
      ;; Start Q chat in the session
      (p/shell "tmux" "send-keys" "-t" tmux-name "q chat" "Enter")
      
      ;; Wait for Q to initialize
      (Thread/sleep 2000)
      
      ;; Update session state
      (swap! session-state assoc session-id
        {:tmux-name tmux-name
         :created (java.time.Instant/now)
         :status "active"
         :last-command nil})
      
      (println (str "✅ Q session created: " tmux-name))
      {:success true :session tmux-name}
      
      (catch Exception e
        (println (str "❌ Error creating Q session: " (.getMessage e)))
        {:success false :error (.getMessage e)}))))

(defn ensure-q-session
  "Ensure Q session exists, create if necessary"
  [session-id]
  (if (session-exists? session-id)
    (do
      (println (str "✅ Q session exists: " (tmux-session-name session-id)))
      {:success true :session (tmux-session-name session-id) :created false})
    (let [result (create-q-session session-id)]
      (assoc result :created true))))

(defn send-command
  "Send command to Q session"
  [session-id command]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      ;; Ensure session exists
      (ensure-q-session session-id)
      
      (println (str "📤 Sending to " tmux-name ": " command))
      
      ;; Send command to tmux session
      (p/shell "tmux" "send-keys" "-t" tmux-name command "Enter")
      
      ;; Update session state
      (swap! session-state update session-id merge
        {:last-command command
         :last-activity (java.time.Instant/now)})
      
      {:success true :command command}
      
      (catch Exception e
        (println (str "❌ Error sending command: " (.getMessage e)))
        {:success false :error (.getMessage e)}))))

(defn send-interrupt
  "Send interrupt signal (Ctrl+C) to Q session"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (println (str "🛑 Sending interrupt to " tmux-name))
      (p/shell "tmux" "send-keys" "-t" tmux-name "C-c")
      {:success true}
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

(defn send-eof
  "Send EOF signal (Ctrl+D) to Q session"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (println (str "📤 Sending EOF to " tmux-name))
      (p/shell "tmux" "send-keys" "-t" tmux-name "C-d")
      {:success true}
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

(defn get-session-output
  "Get current session output"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (let [result (p/shell {:out :string} "tmux" "capture-pane" "-t" tmux-name "-p")]
        {:success true 
         :output (:out result)
         :lines (str/split-lines (:out result))})
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

(defn get-session-output-tail
  "Get last N lines of session output"
  [session-id n]
  (let [result (get-session-output session-id)]
    (if (:success result)
      (assoc result :lines (take-last n (:lines result)))
      result)))

(defn clear-session
  "Clear session screen"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (p/shell "tmux" "send-keys" "-t" tmux-name "clear" "Enter")
      {:success true}
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

(defn get-session-info
  "Get detailed session information"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)
        exists (session-exists? session-id)
        state-info (get @session-state session-id)]
    
    (merge
      {:session-id session-id
       :tmux-name tmux-name
       :exists exists
       :status (if exists "active" "not-found")}
      
      (when exists
        (try
          (let [tmux-info (p/shell {:out :string} "tmux" "list-sessions" "-F" "#{session_name}:#{session_created}:#{session_attached}" "-t" tmux-name)
                info-line (str/trim (:out tmux-info))
                [name created attached] (str/split info-line #":")]
            
            {:created-time created
             :attached (= attached "1")
             :windows (try
                        (let [windows-result (p/shell {:out :string} "tmux" "list-windows" "-t" tmux-name "-F" "#{window_name}")]
                          (str/split-lines (:out windows-result)))
                        (catch Exception e []))})
          
          (catch Exception e
            {:error (.getMessage e)})))
      
      state-info)))

(defn list-all-sessions
  "List all Q sessions"
  []
  (try
    (let [result (p/shell {:out :string} "tmux" "list-sessions" "-F" "#{session_name}")
          all-sessions (str/split-lines (:out result))
          q-sessions (filter #(str/starts-with? % "qq-") all-sessions)]
      
      (mapv (fn [tmux-name]
              (let [session-id (subs tmux-name 3)] ; Remove "qq-" prefix
                (get-session-info session-id)))
            q-sessions))
    
    (catch Exception e
      (println (str "Error listing sessions: " (.getMessage e)))
      [])))

(defn kill-session
  "Kill Q session"
  [session-id]
  (let [tmux-name (tmux-session-name session-id)]
    (try
      (println (str "🗑️ Killing Q session: " tmux-name))
      (p/shell "tmux" "kill-session" "-t" tmux-name)
      
      ;; Remove from state
      (swap! session-state dissoc session-id)
      
      {:success true}
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

(defn attach-session-command
  "Get command to attach to session manually"
  [session-id]
  (str "tmux attach -t " (tmux-session-name session-id)))

;; Session monitoring
(defn monitor-session-output
  "Monitor session output changes (for real-time streaming)"
  [session-id callback]
  (let [tmux-name (tmux-session-name session-id)]
    (future
      (try
        (loop [last-output ""]
          (Thread/sleep 500) ; Check every 500ms
          
          (when (session-exists? session-id)
            (let [result (get-session-output session-id)]
              (when (:success result)
                (let [current-output (:output result)]
                  (when (not= current-output last-output)
                    (callback {:session-id session-id
                               :output current-output
                               :new-content (subs current-output (count last-output))
                               :timestamp (java.time.Instant/now)}))
                  
                  (recur current-output))))))
        
        (catch Exception e
          (println (str "Session monitoring error: " (.getMessage e))))))))

;; Utility functions
(defn session-health-check
  "Check if session is healthy and responsive"
  [session-id]
  (try
    (let [info (get-session-info session-id)]
      (if (:exists info)
        (do
          ;; Try to send a simple command and check response
          (send-command session-id "echo 'health-check'")
          (Thread/sleep 1000)
          
          (let [output-result (get-session-output-tail session-id 5)]
            (if (and (:success output-result)
                     (some #(str/includes? % "health-check") (:lines output-result)))
              {:healthy true :responsive true}
              {:healthy true :responsive false})))
        {:healthy false :responsive false}))
    
    (catch Exception e
      {:healthy false :error (.getMessage e)})))

(defn get-session-stats
  "Get session statistics"
  []
  (let [all-sessions (list-all-sessions)
        active-count (count (filter :exists all-sessions))
        state-count (count @session-state)]
    
    {:total-sessions active-count
     :tracked-sessions state-count
     :sessions all-sessions
     :state @session-state}))
