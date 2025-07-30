(ns qq.command-wrapper
  "Transparent wrapper for Amazon Q commands with monitoring"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [babashka.process :as p]))

;; ============================================================================
;; SESSION MANAGEMENT
;; ============================================================================

(defn get-current-session []
  "Get the current active Q session"
  (try
    (let [current-file (str (System/getProperty "user.home") "/.knock/qq/current")]
      (if (.exists (io/file current-file))
        (str/trim (slurp current-file))
        "default"))
    (catch Exception e
      "default")))

(defn get-session-tmux-name [session-id]
  "Get the tmux session name for a Q session"
  (if (= session-id "default")
    "qq-default"
    (str "qq-" session-id)))

;; ============================================================================
;; COMMAND LOGGING
;; ============================================================================

(defn log-command [session-id command-type command-text metadata]
  "Log command for monitoring with optional metadata"
  (try
    (let [qq-dir (str (System/getProperty "user.home") "/.knock/qq")
          log-file (str qq-dir "/q-commands.json")
          activity (merge {:session-id session-id
                          :command-type command-type
                          :command-text command-text
                          :timestamp (System/currentTimeMillis)
                          :source "q-wrapper"}
                         metadata)]
      
      (.mkdirs (io/file qq-dir))
      
      (let [existing (if (.exists (io/file log-file))
                      (try (json/read-str (slurp log-file) :key-fn keyword)
                           (catch Exception e {:commands []}))
                      {:commands []})
            updated (update existing :commands conj activity)]
        
        (spit log-file (json/write-str updated {:pretty true}))
        (println (str "📝 Logged: " command-type))))
    (catch Exception e
      (println (str "⚠️  Logging failed: " (.getMessage e))))))

;; ============================================================================
;; TMUX FORWARDING
;; ============================================================================

(defn forward-to-q [session-id command]
  "Forward command to Q session and capture response"
  (let [tmux-session (get-session-tmux-name session-id)]
    
    (println (str "🔄 Forwarding to " tmux-session ": " command))
    
    ;; Check if session exists
    (let [check (p/shell {:out :string :err :string} "tmux" "has-session" "-t" tmux-session)]
      (if (zero? (:exit check))
        (do
          ;; Send command
          (p/shell "tmux" "send-keys" "-t" tmux-session command "Enter")
          (Thread/sleep 2000)  ; Wait for response
          
          ;; Capture response
          (let [capture (p/shell {:out :string :err :string} 
                                "tmux" "capture-pane" "-t" tmux-session "-p")]
            (if (zero? (:exit capture))
              (do
                (println "\n📋 Q Response:")
                (println "==============")
                (let [lines (str/split-lines (:out capture))
                      recent (take-last 10 lines)]
                  (doseq [line recent]
                    (println line)))
                (println)
                (:out capture))  ; Return full response for processing
              (do
                (println "⚠️  Could not capture response")
                nil))))
        
        (do
          (println (str "❌ Session " tmux-session " not found"))
          nil)))))

;; ============================================================================
;; COMMAND IMPLEMENTATIONS
;; ============================================================================

(defn cmd-context [& args]
  "Handle /context commands"
  (let [session-id (get-current-session)
        command (str "/context " (str/join " " args))]
    (println (str "🎯 Current session: " session-id))
    (log-command session-id "context" command {:args args})
    (forward-to-q session-id command)))

(defn cmd-save [& args]
  "Handle /save commands with QQ integration"
  (let [session-id (get-current-session)
        save-name (first args)
        command (if save-name 
                  (str "/save " save-name)
                  "/save")]
    
    (println (str "💾 Saving Q conversation" (when save-name (str " as: " save-name))))
    (log-command session-id "save" command {:save-name save-name})
    
    (let [response (forward-to-q session-id command)]
      ;; TODO: Could integrate with QQ session metadata here
      (when response
        (println "✅ Save completed - could integrate with QQ session system")))))

(defn cmd-load [& args]
  "Handle /load commands with QQ integration"
  (let [session-id (get-current-session)
        load-name (first args)
        command (if load-name 
                  (str "/load " load-name)
                  "/load")]
    
    (println (str "📂 Loading Q conversation" (when load-name (str ": " load-name))))
    (log-command session-id "load" command {:load-name load-name})
    
    (let [response (forward-to-q session-id command)]
      ;; TODO: Could update QQ session metadata here
      (when response
        (println "✅ Load completed - could update QQ session context")))))

(defn cmd-tools [& args]
  "Handle /tools commands with monitoring"
  (let [session-id (get-current-session)
        command "/tools"]
    
    (println "🛠️  Checking Q tools and permissions...")
    (log-command session-id "tools" command {:monitoring true})
    
    (let [response (forward-to-q session-id command)]
      (when response
        ;; Parse tools from response for monitoring
        (let [tool-lines (filter #(str/includes? % "tool") (str/split-lines response))]
          (when (seq tool-lines)
            (log-command session-id "tools-detected" 
                        (str "Found " (count tool-lines) " tools")
                        {:tools tool-lines})))))))

(defn cmd-usage [& args]
  "Handle /usage commands with monitoring integration"
  (let [session-id (get-current-session)
        command "/usage"]
    
    (println "📊 Checking context window usage...")
    (log-command session-id "usage" command {})
    
    (let [response (forward-to-q session-id command)]
      (when response
        ;; Parse usage info for monitoring integration
        (let [usage-lines (filter #(or (str/includes? % "token")
                                      (str/includes? % "usage")
                                      (str/includes? % "%")) 
                                 (str/split-lines response))]
          (when (seq usage-lines)
            (log-command session-id "usage-info" 
                        "Context usage captured"
                        {:usage-data usage-lines})))))))

(defn cmd-model [& args]
  "Handle /model commands with tracking"
  (let [session-id (get-current-session)
        model-name (first args)
        command (if model-name 
                  (str "/model " model-name)
                  "/model")]
    
    (println (str "🤖 Model management" (when model-name (str " - selecting: " model-name))))
    (log-command session-id "model" command {:model-name model-name})
    (forward-to-q session-id command)))

(defn cmd-compact [& args]
  "Handle /compact commands with summarization tracking"
  (let [session-id (get-current-session)
        command "/compact"]
    
    (println "🗜️  Compacting conversation to free up context space...")
    (log-command session-id "compact" command {:pre-compact-timestamp (System/currentTimeMillis)})
    
    (let [response (forward-to-q session-id command)]
      (when response
        (log-command session-id "compact-completed" 
                    "Conversation compacted"
                    {:post-compact-timestamp (System/currentTimeMillis)})))))

(defn cmd-editor [& args]
  "Handle /editor commands"
  (let [session-id (get-current-session)
        command "/editor"]
    
    (println "✏️  Opening external editor for prompt composition...")
    (log-command session-id "editor" command {})
    (forward-to-q session-id command)))

;; ============================================================================
;; COMMAND ACTIVITY MONITORING
;; ============================================================================

(defn load-command-log []
  "Load Q command activity log"
  (let [log-file (str (System/getProperty "user.home") "/.knock/qq/q-commands.json")]
    (if (.exists (io/file log-file))
      (try
        (json/read-str (slurp log-file) :key-fn keyword)
        (catch Exception e
          {:commands []}))
      {:commands []})))

(defn display-command-activity []
  "Display recent Q command activity"
  (let [log (load-command-log)
        commands (:commands log)
        recent (take-last 15 commands)]
    
    (println "🔧 Q COMMAND ACTIVITY")
    (println "=====================")
    
    (if (empty? recent)
      (println "❌ No Q command activity recorded")
      (do
        (println (str "📊 Total commands logged: " (count commands)))
        (println (str "🕒 Recent activity (" (count recent) " commands):"))
        (println)
        
        (doseq [cmd recent]
          (let [time-str (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                                 (java.util.Date. (:timestamp cmd)))
                session-str (str "📺 " (:session-id cmd))
                command-str (str "🔧 " (name (:command-type cmd)))]
            
            (println (str "⏰ " time-str " " session-str " " command-str))
            (println (str "   Command: " (:command-text cmd)))
            
            ;; Show additional metadata
            (when (:args cmd)
              (println (str "   Args: " (str/join " " (:args cmd)))))
            (when (:save-name cmd)
              (println (str "   Save name: " (:save-name cmd))))
            (when (:load-name cmd)
              (println (str "   Load name: " (:load-name cmd))))
            (when (:model-name cmd)
              (println (str "   Model: " (:model-name cmd))))
            (when (:tools cmd)
              (println (str "   Tools detected: " (count (:tools cmd)))))
            (when (:usage-data cmd)
              (println (str "   Usage info: " (first (:usage-data cmd)))))
            
            (println)))))))

(defn display-command-summary []
  "Display summary of Q command usage"
  (let [log (load-command-log)
        commands (:commands log)
        by-type (group-by :command-type commands)
        by-session (group-by :session-id commands)]
    
    (println "📊 Q COMMAND USAGE SUMMARY")
    (println "===========================")
    
    (println (str "📈 Total commands: " (count commands)))
    (println (str "🖥️  Active sessions: " (count by-session)))
    (println)
    
    (println "📋 Commands by type:")
    (doseq [[cmd-type cmd-list] (sort-by #(count (second %)) > by-type)]
      (println (str "  " (name cmd-type) ": " (count cmd-list) " times")))
    
    (println)
    (println "📺 Commands by session:")
    (doseq [[session cmd-list] (sort-by #(count (second %)) > by-session)]
      (println (str "  " session ": " (count cmd-list) " commands")))))
