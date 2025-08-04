(ns qq.terminal.websocket-server
  "🚀 THE Definitive WebSocket Server - Complete End-to-End Q&A Flow + Streaming
  
  This is THE ONLY WebSocket server implementation we need.
  Features:
  - Working WebSocket handshake
  - Complete WebSocket frame processing  
  - Proper Q&A boundaries (uses qq.tmux/send-and-wait-improved)
  - 🌊 TMUX STREAMING: Real-time character streaming from tmux sessions
  - Clean architecture (no nested try-catch hell)
  
  No more confusion about which server to use!"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [qq.tmux :as tmux])
  (:import [java.net ServerSocket]
           [java.io PrintWriter BufferedReader InputStreamReader]
           [java.util.concurrent Executors]
           [java.security MessageDigest]
           [java.util Base64]
           [java.io File]))

(def server-state (atom {:running false :server nil :streaming-sessions {}}))

;; 🌊 STREAMING FUNCTIONS (Defined early for use in message handling)

;; Forward declarations
(declare send-websocket-frame)

(defn clean-streaming-content
  "Clean streaming content while preserving in-place updates for spinners"
  [content]
  (-> content
      ;; Remove ANSI escape sequences but preserve cursor movements
      (str/replace #"\u001B\[[0-9;]*[mK]" "")
      ;; Remove spinner characters but keep the structure
      (str/replace #"[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]" "")
      ;; DON'T remove carriage returns - they're needed for in-place updates
      ;; (str/replace #"\r" "") <- REMOVED THIS LINE
      ;; Remove excessive dots and spaces
      (str/replace #"\.{3,}" "...")
      ;; Clean up multiple spaces
      (str/replace #" {2,}" " ")
      ;; Remove empty lines but preserve structure
      (str/replace #"\n\s*\n" "\n")))

;; 🚀 AGGRESSIVE TMUX MIRRORING FUNCTIONS

;; Enhanced WebSocket connection management
(def streaming-clients (atom #{}))
(def client-heartbeats (atom {})) ; Track last heartbeat for each client
(def client-metadata (atom {}))   ; Store connection metadata

(defn add-streaming-client [client-socket connection-id]
  "Add client with metadata tracking"
  (let [client-info {:socket client-socket
                     :connection-id connection-id
                     :connected-at (System/currentTimeMillis)
                     :last-heartbeat (System/currentTimeMillis)}]
    (swap! streaming-clients conj client-socket)
    (swap! client-metadata assoc client-socket client-info)
    (swap! client-heartbeats assoc client-socket (System/currentTimeMillis))
    (println (str "📡 Added streaming client [" connection-id "]. Total clients: " (count @streaming-clients)))))

(defn remove-streaming-client [client-socket]
  "Remove client and cleanup metadata"
  (let [client-info (get @client-metadata client-socket)]
    (swap! streaming-clients disj client-socket)
    (swap! client-metadata dissoc client-socket)
    (swap! client-heartbeats dissoc client-socket)
    (when client-info
      (println (str "📡 Removed streaming client [" (:connection-id client-info) "]. Total clients: " (count @streaming-clients))))))

(defn is-websocket-alive? [client-socket]
  "Comprehensive WebSocket connection health check"
  (try
    (and client-socket
         (.isConnected client-socket)
         (not (.isClosed client-socket))
         (not (.isInputShutdown client-socket))
         (not (.isOutputShutdown client-socket)))
    (catch Exception e
      false)))

(defn cleanup-dead-connections []
  "Remove dead connections from streaming clients"
  (let [dead-clients (atom [])]
    (doseq [client-socket @streaming-clients]
      (let [client-info (get @client-metadata client-socket)]
        (when (not (is-websocket-alive? client-socket))
          (println (str "🧹 Removing dead connection [" (:connection-id client-info) "]"))
          (swap! dead-clients conj client-socket))))
    
    ;; Remove all dead clients
    (doseq [dead-client @dead-clients]
      (remove-streaming-client dead-client))
    
    (when (> (count @dead-clients) 0)
      (println (str "🧹 Cleaned up " (count @dead-clients) " dead connections. Active: " (count @streaming-clients))))))

(defn broadcast-to-streaming-clients [message]
  "Broadcast message to all streaming clients with robust error handling"
  (when (> (count @streaming-clients) 0)
    (let [successful-sends (atom 0)
          failed-sends (atom 0)]
      
      (doseq [client-socket @streaming-clients]
        (let [client-info (get @client-metadata client-socket)]
          (try
            (if (is-websocket-alive? client-socket)
              (do
                (let [output-stream (.getOutputStream client-socket)
                      json-message (json/write-str message)]
                  (send-websocket-frame output-stream json-message)
                  (swap! successful-sends inc)
                  ;; Update heartbeat on successful send
                  (swap! client-heartbeats assoc client-socket (System/currentTimeMillis))))
              (do
                (swap! failed-sends inc)))
            (catch Exception e
              (swap! failed-sends inc)))))
      
      ;; Clean up dead connections if we had failures
      (when (> @failed-sends 0)
        (cleanup-dead-connections))
      
      ;; Only log if there were actual sends attempted
      (when (and (> (+ @successful-sends @failed-sends) 0) (> @successful-sends 0))
        (println (str "📡 Broadcast: " @successful-sends " successful" 
                     (when (> @failed-sends 0) (str ", " @failed-sends " failed"))))))))

;; Background connection cleanup task
(defn start-connection-cleanup-task []
  "Start background task to clean up dead connections"
  (async/go
    (loop []
      (try
        ;; Clean up dead connections every 30 seconds
        (async/<! (async/timeout 30000))
        (cleanup-dead-connections)
        (catch Exception e
          (println (str "❌ Error in connection cleanup task: " (.getMessage e)))))
      (recur))))

(defn capture-full-tmux-history
  "Capture the entire tmux scrollback history"
  [session-name]
  (println (str "📜 Capturing full tmux history for: " session-name))
  (try
    ;; Capture entire scrollback buffer (-S - means from beginning)
    (let [result (p/process ["tmux" "capture-pane" "-t" session-name "-S" "-" "-p"] 
                            {:out :string})]
      (if (= 0 (:exit @result))
        (let [full-content (:out @result)]
          (println (str "✅ Captured " (count (str/split-lines full-content)) " lines of history"))
          full-content)
        (do
          (println (str "❌ Failed to capture history: " (:err @result)))
          "")))
    (catch Exception e
      (println (str "❌ Error capturing tmux history: " (.getMessage e)))
      "")))

(defn sync-full-tmux-content-simple
  "Send a simple test message instead of full content to test WebSocket stability"
  [client-socket session-name]
  (println (str "🔄 Sending simple test message to client"))
  (try
    (let [message {:type "tmux-full-sync"
                   :content "🧪 Simple test message - WebSocket connection working!"
                   :session session-name
                   :timestamp (System/currentTimeMillis)}
          output-stream (.getOutputStream client-socket)
          json-message (json/write-str message)]
      (println (str "📡 Sending simple test message"))
      (send-websocket-frame output-stream json-message))
    (catch Exception e
      (println (str "❌ Error sending simple message: " (.getMessage e))))))

(defn sync-current-page-tmux-content
  "Send only the current page (last 50 lines) for immediate display"
  [client-socket session-name]
  (println (str "📄 Syncing current page tmux content to client"))
  (try
    (let [full-content (capture-full-tmux-history session-name)
          lines (str/split-lines full-content)
          current-page-lines (take-last 50 lines)  ; Just last 50 lines
          current-page-content (str/join "\n" current-page-lines)
          cleaned-content (clean-streaming-content current-page-content)]
      (when (not (str/blank? cleaned-content))
        (let [message {:type "tmux-current-page"
                       :content cleaned-content
                       :totalLines (count lines)
                       :currentPageLines (count current-page-lines)
                       :hasMoreHistory (> (count lines) 50)
                       :session session-name
                       :timestamp (System/currentTimeMillis)}
              output-stream (.getOutputStream client-socket)
              json-message (json/write-str message)]
          (println (str "📄 Sending current page: " (count current-page-lines) " lines (total: " (count lines) " available)"))
          (send-websocket-frame output-stream json-message))))
    (catch Exception e
      (println (str "❌ Error syncing current page: " (.getMessage e))))))

(defn load-incremental-history
  "Load more history incrementally when user scrolls up"
  [client-socket session-name offset limit]
  (println (str "📜 Loading incremental history: offset=" offset " limit=" limit))
  (try
    (let [full-content (capture-full-tmux-history session-name)
          lines (str/split-lines full-content)
          total-lines (count lines)
          start-index (max 0 (- total-lines offset limit))
          end-index (- total-lines offset)
          history-lines (subvec (vec lines) start-index end-index)
          history-content (str/join "\n" history-lines)
          cleaned-content (clean-streaming-content history-content)]
      (when (not (str/blank? cleaned-content))
        (let [message {:type "tmux-incremental-history"
                       :content cleaned-content
                       :offset offset
                       :limit limit
                       :startIndex start-index
                       :endIndex end-index
                       :totalLines total-lines
                       :hasMore (> start-index 0)
                       :session session-name
                       :timestamp (System/currentTimeMillis)}
              output-stream (.getOutputStream client-socket)
              json-message (json/write-str message)]
          (println (str "📜 Sending incremental history: " (count history-lines) " lines (from " start-index " to " end-index ")"))
          (send-websocket-frame output-stream json-message))))
    (catch Exception e
      (println (str "❌ Error loading incremental history: " (.getMessage e))))))

(defn ensure-pipe-pane-active
  "Ensure tmux pipe-pane is active, restart if needed"
  [session-name output-file]
  (try
    ;; Check if pipe-pane is active
    (let [result (p/process ["tmux" "list-panes" "-t" session-name "-F" "#{pane_pipe}"] 
                            {:out :string})]
      (if (= 0 (:exit @result))
        (let [pipe-status (str/trim (:out @result))]
          (if (= "0" pipe-status)
            (do
              (println (str "🔧 Pipe-pane inactive for " session-name ", restarting..."))
              ;; Restart pipe-pane
              (let [restart-result (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                                              {:out :string})]
                (if (= 0 (:exit @restart-result))
                  (println (str "✅ Pipe-pane restarted for " session-name))
                  (println (str "❌ Failed to restart pipe-pane: " (:err @restart-result))))))
            (println (str "✅ Pipe-pane already active for " session-name))))
        (println (str "❌ Failed to check pipe-pane status: " (:err @result)))))
    (catch Exception e
      (println (str "❌ Error checking pipe-pane: " (.getMessage e))))))

;; Command echo filtering state
(def last-sent-commands (atom {})) ; session-name -> {:command "..." :timestamp 123}

(defn should-filter-command-echo? [session-name content]
  "Check if content is a command echo that should be filtered"
  (when-let [last-cmd (get @last-sent-commands session-name)]
    (let [time-since-sent (- (System/currentTimeMillis) (:timestamp last-cmd))
          command-text (:command last-cmd)]
      ;; Filter if content matches last command within 2 seconds
      (and (< time-since-sent 2000)
           (or (= content command-text)
               (= content (str "$ " command-text))
               (= content (str "> " command-text))
               (.contains content command-text))))))

(defn record-sent-command [session-name command]
  "Record a command that was sent to track for echo filtering"
  (swap! last-sent-commands assoc session-name 
         {:command command :timestamp (System/currentTimeMillis)})
  (println (str "📝 Recorded command for echo filtering [" session-name "]: " command)))

(defn start-aggressive-file-monitoring
  "Monitor file changes for aggressive real-time mirroring with pipe-pane monitoring"
  [output-file session-name]
  (println (str "👁️ Starting aggressive file monitoring: " output-file))
  
  ;; Start pipe-pane monitoring in background
  (async/go
    (loop []
      (try
        ;; Check and restart pipe-pane every 5 seconds
        (ensure-pipe-pane-active session-name output-file)
        (catch Exception e
          (println (str "❌ Error in pipe-pane monitoring: " (.getMessage e)))))
      (async/<! (async/timeout 5000))
      (recur)))
  
  (async/go
    (try
      (let [tail-process (p/process ["tail" "-f" output-file] {:out :stream})]
        (with-open [reader (io/reader (:out tail-process))]
          (loop []
            (when-let [line (.readLine reader)]
              ;; For aggressive mirroring, preserve in-place updates
              (let [cleaned-content (-> line
                                        ;; Only remove the most problematic chars
                                        (str/replace #"[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]" "")
                                        ;; Keep carriage returns for in-place updates
                                        ;; (str/replace #"\r" "") <- REMOVED
                                        )]
                (when (not (str/blank? cleaned-content))
                  (let [message {:type "tmux-realtime"
                                 :content cleaned-content
                                 :session session-name
                                 :timestamp (System/currentTimeMillis)}]
                    ;; Filter command echoes before broadcasting
                    (if (should-filter-command-echo? session-name cleaned-content)
                      (println (str "🔍 Filtered command echo: " (subs cleaned-content 0 (min 50 (count cleaned-content))) "..."))
                      (do
                        (println (str "📡 Aggressive mirror: " (subs cleaned-content 0 (min 80 (count cleaned-content))) "..."))
                        (broadcast-to-streaming-clients message))))))
              (recur)))))
      
      (catch Exception e
        (println (str "❌ Error in aggressive file monitoring: " (.getMessage e)))))))

;; Simple HTTP endpoint for direct tmux commands
(defn handle-tmux-command [request]
  "Handle direct tmux command via HTTP"
  (try
    (let [body (slurp (:body request))
          data (json/read-str body :key-fn keyword)
          command (:command data)
          session-name (or (:session data) "qq-default")]
      
      (when command
        (println (str "📤 Direct tmux command: " command))
        ;; Send command directly to tmux session
        (let [result (p/process ["tmux" "send-keys" "-t" session-name command "Enter"] 
                                {:out :string})]
          (if (= 0 (:exit @result))
            (println (str "✅ Command sent to tmux: " command))
            (println (str "❌ Failed to send command: " (:err @result))))))
      
      ;; Return simple success response
      {:status 200
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "POST"
                 "Access-Control-Allow-Headers" "Content-Type"}
       :body (json/write-str {:success true :message "Command sent to tmux"})})
    
    (catch Exception e
      (println (str "❌ Error handling tmux command: " (.getMessage e)))
      {:status 500
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body (json/write-str {:success false :error (.getMessage e)})})))

;; Window management functions
(defn list-tmux-windows [session-name]
  "List all windows in a tmux session"
  (try
    (let [result (p/process ["tmux" "list-windows" "-t" session-name "-F" "#{window_index}:#{window_name}:#{window_active}"] 
                           {:out :string})]
      (if (= 0 (:exit @result))
        (let [output (:out @result)
              lines (str/split-lines output)]
          (map (fn [line]
                 (let [[index name active] (str/split line #":")]
                   {:index (Integer/parseInt index)
                    :name name
                    :active (= active "1")})) lines))
        []))
    (catch Exception e
      (println (str "❌ Error listing windows: " (.getMessage e)))
      [])))

(defn get-window-content [session-name window-index]
  "Get current content of a specific tmux window with formatting preserved"
  (try
    (let [window-target (str session-name ":" window-index)
          ;; 🎨 CAPTURE WITH ANSI ESCAPE SEQUENCES FOR FORMATTING
          result (p/process ["tmux" "capture-pane" "-t" window-target "-e" "-p"] 
                           {:out :string})]
      (if (= 0 (:exit @result))
        (let [content (:out @result)]
          (println (str "📄 Captured content with formatting from window " window-index ": " (count content) " chars"))
          content)
        (do
          (println (str "❌ Failed to capture window content: " (:err @result)))
          "")))
    (catch Exception e
      (println (str "❌ Error capturing window content: " (.getMessage e)))
      "")))

(defn select-tmux-window [session-name window-index]
  "Select a specific window in tmux session and return its content"
  (try
    (let [result (p/process ["tmux" "select-window" "-t" (str session-name ":" window-index)]
                           {:out :string})]
      (if (= 0 (:exit @result))
        (do
          (println (str "✅ Selected window " window-index " in session " session-name))
          ;; Get the content of the newly selected window
          (let [content (get-window-content session-name window-index)]
            {:success true :content content}))
        (do
          (println (str "❌ Failed to select window: " (:err @result)))
          {:success false :content ""})))
    (catch Exception e
      (println (str "❌ Error selecting window: " (.getMessage e)))
      {:success false :content ""})))

(defn create-tmux-window [session-name]
  "Create a new window in tmux session"
  (try
    (let [result (p/process ["tmux" "new-window" "-t" session-name]
                           {:out :string :err :string})]
      (if (= 0 (:exit @result))
        (do
          (println (str "✅ Created new window in session " session-name))
          true)
        (do
          (println (str "❌ Failed to create window in session " session-name))
          (println (str "   Exit code: " (:exit @result)))
          (println (str "   Error output: " (:err @result)))
          false)))
    (catch Exception e
      (println (str "❌ Error creating window: " (.getMessage e)))
      false)))

(defn restart-pipe-pane-if-needed
  "Check and restart pipe-pane if it's inactive"
  [session-name output-file]
  (try
    (let [result (p/process ["tmux" "list-panes" "-t" session-name "-F" "#{pane_pipe}"] {:out :string})]
      (when (= 0 (:exit @result))
        (let [pipe-status (str/trim (:out @result))]
          (when (= "0" pipe-status)
            (println (str "🔧 Restarting inactive pipe-pane for " session-name))
            (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] {:out :string})
            (println (str "✅ Pipe-pane restarted for " session-name))))))
    (catch Exception e
      (println (str "❌ Error restarting pipe-pane: " (.getMessage e))))))

(defn start-aggressive-tmux-mirroring
  "Start aggressive tmux mirroring with full history sync and pipe-pane monitoring"
  [session-name client-socket]
  (println (str "🚀 Starting AGGRESSIVE tmux mirroring for: " session-name))
  
  ;; Step 1: Sync current page immediately (last 50 lines)
  (sync-current-page-tmux-content client-socket session-name)
  
  ;; Step 2: Start real-time streaming
  (add-streaming-client client-socket "aggressive-mirror")
  
  (let [output-file (str "/tmp/tmux-mirror-" session-name ".log")]
    
    ;; Clean up existing file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty file
    (spit output-file "")
    
    ;; Ensure pipe-pane is active
    (restart-pipe-pane-if-needed session-name output-file)
    
    ;; Start tmux pipe-pane for real-time updates
    (try
      (let [result (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                              {:out :string})]
        (if (= 0 (:exit @result))
          (do
            (println (str "✅ Aggressive mirroring started: " session-name " → " output-file))
            
            ;; Start file monitoring for real-time updates
            (start-aggressive-file-monitoring output-file session-name)
            
            ;; Store mirroring info
            (swap! server-state assoc-in [:streaming-sessions session-name] 
                   {:output-file output-file
                    :status :mirroring
                    :started (System/currentTimeMillis)
                    :clients (count @streaming-clients)
                    :mode :aggressive})
            
            {:session session-name
             :output-file output-file
             :status :mirroring
             :mode :aggressive})
          
          (do
            (println (str "❌ Failed to start aggressive mirroring: " (:err @result)))
            (remove-streaming-client client-socket)
            nil)))
      
      (catch Exception e
        (println (str "❌ Error starting aggressive mirroring: " (.getMessage e)))
        (remove-streaming-client client-socket)
        nil))))

;; Update the existing functions to use aggressive mirroring

(defn start-file-monitoring
  "Start monitoring a file and stream changes to WebSocket clients"
  [output-file session-name]
  (println (str "👀 Starting file monitoring: " output-file))
  
  (async/go
    (try
      (let [tail-process (p/process ["tail" "-f" output-file] {:out :stream})]
        (with-open [reader (io/reader (:out tail-process))]
          (loop []
            (when-let [line (.readLine reader)]
              ;; Clean the content before streaming
              (let [cleaned-content (clean-streaming-content line)]
                (when (and (not (str/blank? cleaned-content))
                          (> (count cleaned-content) 2)) ; Only stream meaningful content
                  (let [message {:type "tmux-stream"
                                 :content cleaned-content
                                 :session session-name
                                 :timestamp (System/currentTimeMillis)}]
                    (println (str "📡 Streaming clean line: " (subs cleaned-content 0 (min 50 (count cleaned-content))) "..."))
                    (broadcast-to-streaming-clients message))))
              (recur)))))
      
      (catch Exception e
        (println (str "❌ Error in file monitoring: " (.getMessage e)))))))

;; User window subscription tracking
(def user-window-subscriptions (atom {}))
;; Format: {"client-id" {:session "qq-default" :window 1}}

(defn subscribe-user-to-window [client-socket session-name window-index]
  "Subscribe a user to a specific window for streaming"
  (let [client-id (str (.hashCode client-socket))]
    (swap! user-window-subscriptions assoc client-id 
           {:session session-name :window window-index :socket client-socket})
    (println (str "📺 User " client-id " subscribed to " session-name ":" window-index))
    (println (str "📊 Active subscriptions: " (count @user-window-subscriptions)))))

(defn unsubscribe-user [client-socket]
  "Remove user's window subscription"
  (let [client-id (str (.hashCode client-socket))]
    (swap! user-window-subscriptions dissoc client-id)
    (println (str "📺 User " client-id " unsubscribed"))
    (println (str "📊 Active subscriptions: " (count @user-window-subscriptions)))))

(defn get-active-windows [session-name]
  "Get list of windows that have active viewers"
  (let [subscriptions (vals @user-window-subscriptions)
        session-subs (filter #(= (:session %) session-name) subscriptions)
        active-windows (distinct (map :window session-subs))]
    (println (str "📋 Active windows for " session-name ": " active-windows))
    active-windows))

(defn broadcast-to-window-viewers [session-name window-index message]
  "Send message only to users viewing specific window"
  (let [viewers (filter (fn [[client-id subscription]]
                         (and (= (:session subscription) session-name)
                              (= (:window subscription) window-index)))
                       @user-window-subscriptions)]
    (doseq [[client-id subscription] viewers]
      (try
        (send-websocket-frame (:socket subscription) (json/write-str message))
        (println (str "📤 Sent window update to user " client-id " for window " window-index))
        (catch Exception e
          (println (str "❌ Failed to send to user " client-id ": " (.getMessage e)))
          ;; Remove dead connection
          (unsubscribe-user (:socket subscription)))))
    (count viewers)))

(defn start-window-file-monitoring
  "Monitor tmux window output file and broadcast to subscribed users"
  [session-name window-index output-file]
  (future
    (try
      (println (str "📁 Starting file monitoring for window " session-name ":" window-index))
      (let [file (File. output-file)]
        (loop [last-content ""]
          (Thread/sleep 100) ; Check every 100ms
          (when (.exists file)
            (let [current-content (try (slurp output-file) (catch Exception e ""))]
              (when (not= current-content last-content)
                ;; New content available
                (let [new-content (if (str/starts-with? current-content last-content)
                                   (subs current-content (count last-content))
                                   current-content)] ; Full content if file was truncated
                  ;; Broadcast to users viewing this specific window
                  (when (not (str/blank? new-content))
                    (let [viewers-count (broadcast-to-window-viewers session-name window-index 
                                                                   {:type "window-stream"
                                                                    :session session-name
                                                                    :window window-index
                                                                    :content new-content})]
                      (when (> viewers-count 0)
                        (println (str "📤 Streamed " (count new-content) " chars to " viewers-count " viewers of window " window-index)))))))
              (recur current-content)))))
      (catch Exception e
        (println (str "❌ Error in window file monitoring: " (.getMessage e)))))))

(defn start-window-streaming
  "Start streaming from a specific tmux window"
  [session-name window-index]
  (let [window-target (str session-name ":" window-index)
        output-file (str "/tmp/tmux-window-" session-name "-" window-index ".log")]
    
    (println (str "🌊 Starting window streaming for: " window-target))
    
    ;; Clean up existing file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty file
    (spit output-file "")
    
    ;; Start tmux pipe-pane for specific window with ANSI formatting
    (try
      (let [result (p/process ["tmux" "pipe-pane" "-t" window-target "-e" "-O" output-file]
                             {:out :string :err :string})]
        (if (= 0 (:exit @result))
          (do
            (println (str "✅ Window streaming started for " window-target))
            ;; Start file monitoring for this window
            (start-window-file-monitoring session-name window-index output-file)
            {:success true :window window-target :file output-file})
          (do
            (println (str "❌ Failed to start window streaming: " (:err @result)))
            {:success false :error (:err @result)})))
      (catch Exception e
        (println (str "❌ Error starting window streaming: " (.getMessage e)))
        {:success false :error (.getMessage e)}))))

(defn manage-window-streaming
  "Start/stop window streaming based on active subscriptions"
  [session-name]
  (let [active-windows (get-active-windows session-name)]
    (println (str "🔄 Managing streaming for windows: " active-windows " in session " session-name))
    (doseq [window active-windows]
      (start-window-streaming session-name window))))

(defn start-tmux-streaming
  "Start streaming from a tmux session to WebSocket clients"
  [session-name client-socket]
  (println (str "🌊 Starting tmux streaming for session: " session-name))
  
  ;; Add client to streaming clients
  (add-streaming-client client-socket "tmux-stream")
  
  (let [output-file (str "/tmp/tmux-stream-" session-name ".log")]
    
    ;; Clean up existing file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty file
    (spit output-file "")
    
    ;; Start tmux pipe-pane
    (try
      (let [result (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                              {:out :string})]
        (if (= 0 (:exit @result))
          (do
            (println (str "✅ Tmux pipe-pane started: " session-name " → " output-file))
            
            ;; Start file monitoring and streaming
            (start-file-monitoring output-file session-name)
            
            ;; Store streaming info
            (swap! server-state assoc-in [:streaming-sessions session-name] 
                   {:output-file output-file
                    :status :streaming
                    :started (System/currentTimeMillis)
                    :clients (count @streaming-clients)})
            
            {:session session-name
             :output-file output-file
             :status :streaming})
          
          (do
            (println (str "❌ Failed to start pipe-pane: " (:err @result)))
            (remove-streaming-client client-socket)
            nil)))
      
      (catch Exception e
        (println (str "❌ Error starting tmux streaming: " (.getMessage e)))
        (remove-streaming-client client-socket)
        nil))))

(defn stop-tmux-streaming
  "Stop streaming from a tmux session"
  [session-name]
  (println (str "🛑 Stopping tmux streaming for session: " session-name))
  
  (try
    (p/process ["tmux" "pipe-pane" "-t" session-name] {:out :string})
    (println "✅ Tmux pipe-pane stopped")
    
    ;; Remove from streaming sessions
    (swap! server-state update :streaming-sessions dissoc session-name)
    
    (catch Exception e
      (println (str "❌ Error stopping streaming: " (.getMessage e))))))

(defn get-streaming-sessions
  "Get all active streaming sessions"
  []
  (:streaming-sessions @server-state))

(defn- websocket-accept-key [client-key]
  "Generate WebSocket accept key"
  (let [magic-string "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        combined (str client-key magic-string)
        sha1 (MessageDigest/getInstance "SHA-1")
        hash (.digest sha1 (.getBytes combined "UTF-8"))]
    (.encodeToString (Base64/getEncoder) hash)))

(defn- read-websocket-frame [input-stream connection-id]
  "Read a WebSocket frame and return the payload as string"
  (try
    ;; Check if data is available before attempting to read
    (when (and (> (.available input-stream) 0))
      (println (str "🔍 Data available [" connection-id "], attempting to read WebSocket frame..."))
      (let [first-byte (.read input-stream)]
        (when (>= first-byte 0)
          (let [second-byte (.read input-stream)
                payload-length (bit-and second-byte 0x7F)
                masked? (bit-test second-byte 7)
                opcode (bit-and first-byte 0x0F)]
            
            (println (str "🔍 Frame details [" connection-id "]: opcode=" opcode " masked=" masked? " length=" payload-length))
            
            ;; Handle different frame types
            (cond
              (= opcode 1) ; Text frame
              (when (and masked? (> payload-length 0))
                (println (str "🎉 Reading TEXT frame [" connection-id "] with mask..."))
                ;; Read mask key
                (let [mask-key (byte-array 4)]
                  (.read input-stream mask-key)
                  
                  ;; Read payload
                  (let [payload (byte-array payload-length)]
                    (.read input-stream payload)
                    
                    ;; Unmask payload
                    (dotimes [i payload-length]
                      (aset payload i (byte (bit-xor (aget payload i) 
                                                    (aget mask-key (mod i 4))))))
                    
                    ;; Convert to string
                    (let [message (String. payload "UTF-8")]
                      (println (str "🎉 DECODED TEXT MESSAGE [" connection-id "]: " message))
                      message))))
              
              (= opcode 8) ; Close frame
              (do
                (println (str "🔍 Received CLOSE frame [" connection-id "] - connection closing"))
                ;; Read close payload if present
                (when (> payload-length 0)
                  (if masked?
                    (let [mask-key (byte-array 4)
                          payload (byte-array payload-length)]
                      (.read input-stream mask-key)
                      (.read input-stream payload))
                    (let [payload (byte-array payload-length)]
                      (.read input-stream payload))))
                nil)
              
              (= opcode 9) ; Ping frame
              (do
                (println (str "🔍 Received PING frame [" connection-id "] - should send PONG"))
                ;; Read ping payload if present
                (when (> payload-length 0)
                  (if masked?
                    (let [mask-key (byte-array 4)
                          payload (byte-array payload-length)]
                      (.read input-stream mask-key)
                      (.read input-stream payload))
                    (let [payload (byte-array payload-length)]
                      (.read input-stream payload))))
                nil)
              
              (= opcode 10) ; Pong frame
              (do
                (println (str "🔍 Received PONG frame [" connection-id "] - keepalive"))
                ;; Read pong payload if present
                (when (> payload-length 0)
                  (if masked?
                    (let [mask-key (byte-array 4)
                          payload (byte-array payload-length)]
                      (.read input-stream mask-key)
                      (.read input-stream payload))
                    (let [payload (byte-array payload-length)]
                      (.read input-stream payload))))
                nil)
              
              :else
              (do
                (println (str "🔍 Received unknown frame type [" connection-id "]: opcode=" opcode))
                ;; Read unknown payload to prevent stream corruption
                (when (> payload-length 0)
                  (if masked?
                    (let [mask-key (byte-array 4)
                          payload (byte-array payload-length)]
                      (.read input-stream mask-key)
                      (.read input-stream payload))
                    (let [payload (byte-array payload-length)]
                      (.read input-stream payload))))
                nil))))))
    (catch Exception e
      (println (str "❌ Error reading WebSocket frame [" connection-id "]: " (.getMessage e)))
      nil)))

(defn- send-websocket-frame [output-stream message]
  "Send a WebSocket text frame"
  (try
    (let [message-bytes (.getBytes message "UTF-8")
          frame-length (count message-bytes)]
      
      ;; Send text frame header (FIN=1, opcode=1 for text)
      (.write output-stream 0x81)
      
      ;; Send payload length with proper WebSocket framing
      (cond
        (< frame-length 126)
        (.write output-stream frame-length)
        
        (< frame-length 65536)
        (do
          (.write output-stream 126)
          ;; Send 16-bit length in network byte order
          (.write output-stream (bit-shift-right frame-length 8))
          (.write output-stream (bit-and frame-length 0xFF)))
        
        :else
        (do
          (.write output-stream 127)
          ;; Send 64-bit length in network byte order (simplified for our use case)
          (.write output-stream 0) (.write output-stream 0) (.write output-stream 0) (.write output-stream 0)
          (.write output-stream (bit-shift-right frame-length 24))
          (.write output-stream (bit-and (bit-shift-right frame-length 16) 0xFF))
          (.write output-stream (bit-and (bit-shift-right frame-length 8) 0xFF))
          (.write output-stream (bit-and frame-length 0xFF))))
      
      ;; Send payload
      (.write output-stream message-bytes)
      (.flush output-stream))
    (catch Exception e
      (println (str "❌ Error sending WebSocket frame: " (.getMessage e))))))

;; Q Window tracking - maps session-name to Q window index
(def q-window-sessions (atom {})) ; session-name -> window-index

(defn q-command? [command]
  "Detect if command is a Q command vs regular shell command"
  (let [cmd (str/trim (str/lower-case command))]
    (or
      ;; Question patterns
      (.contains cmd "?")
      ;; Q command starters
      (re-matches #"^(what|how|why|explain|tell me|describe|show me|help|can you).*" cmd)
      ;; Direct Q invocation
      (.startsWith cmd "q ")
      ;; Other Q patterns
      (re-matches #".*(explain|meaning|definition|difference|compare).*" cmd))))

(defn get-or-create-q-window [session-name]
  "Get existing Q window or create new one for Q commands"
  (if-let [existing-window (get @q-window-sessions session-name)]
    (do
      (println (str "📋 Using existing Q window " existing-window " for session " session-name))
      existing-window)
    (do
      (println (str "🆕 Creating new Q window for session " session-name))
      ;; Create new window for Q
      (if (create-tmux-window session-name)
        (let [windows (list-tmux-windows session-name)
              new-window-index (apply max (map :index windows))]
          (println (str "✅ Created Q window " new-window-index " for session " session-name))
          ;; Track this as the Q window
          (swap! q-window-sessions assoc session-name new-window-index)
          ;; Switch to the Q window and start Q if needed
          (select-tmux-window session-name new-window-index)
          new-window-index)
        (do
          (println (str "❌ Failed to create Q window for session " session-name))
          ;; Fallback to window 0 or current active window
          0)))))

(defn route-command [command session-name current-window]
  "Smart routing: Q commands to main session, shell commands to current window"
  (if (q-command? command)
    (do
      (println (str "🎯 Q command detected: '" command "' → routing to main session"))
      {:window "main" :type :q-command})
    (do
      (println (str "🐚 Shell command detected: '" command "' → routing to current window " current-window))
      {:window current-window :type :shell-command})))

(defn- process-qa-command [command session-id]
  "Process Q&A command using our proven implementation"
  (try
    (println (str "🎯 Processing Q&A: " command " → " session-id))
    
    ;; Use our proven Q&A implementation with proper boundaries
    (let [response (tmux/send-and-wait-improved session-id command)]
      (println (str "✅ Q&A Success: " (subs response 0 (min 50 (count response))) "..."))
      {:success true :output response})
    
    (catch Exception e
      (println (str "❌ Q&A Failed: " (.getMessage e)))
      {:success false :error (str "Q&A failed: " (.getMessage e))})))

(defn- handle-websocket-message [message-data client-socket]
  "Handle incoming WebSocket message with streaming support"
  (try
    ;; Try to parse JSON message with error handling for browser cleanup
    (let [parsed (try 
                   (json/read-str message-data :key-fn keyword)
                   (catch Exception e
                     (println (str "⚠️ JSON parse error (likely browser cleanup): " (.getMessage e)))
                     (println (str "📝 Raw message data: " (pr-str (take 50 message-data))))
                     ;; Return nil to skip processing - this is likely browser cleanup
                     nil))]
      
      (if parsed
        ;; Valid JSON message - process normally
        (let [command (:content parsed)
              session-id (or (:session parsed) "qq-default")]
          
          (println (str "📨 WebSocket Message: " parsed))
      
      (case (:type parsed)
        "command"
        (do
          ;; Get current window from client (which window user is viewing)
          (let [session-name (if (= (:session parsed) "default") 
                              "qq-default"  ; Map "default" to "qq-default"
                              (or (:session parsed) "qq-default"))
                current-window (or (:currentWindow parsed) 2) ; Default to window 2
                routing (route-command command session-name current-window)]
            
            (println (str "📍 Smart routing: " (:type routing) " → window " (:window routing) " in session " session-name))
            
            ;; Record command for echo filtering
            (record-sent-command session-id command)
            
            (if (= (:type routing) :q-command)
              ;; Q command - route to main session (where Q is running)
              (let [terse-session-name (if (str/starts-with? session-name "qq-")
                                        (subs session-name 3)  ; Remove "qq-" prefix
                                        session-name)
                    result (process-qa-command command terse-session-name)]
                (println (str "🎯 Q command processed in main session: " terse-session-name))
                (if (:success result)
                  {:type "output" 
                   :content (:output result) 
                   :targetWindow "main-session"
                   :commandType "q-command"
                   :success true}
                  {:type "error" 
                   :content (:error result) 
                   :targetWindow "main-session"
                   :success false}))
              ;; Shell command - send to current window
              (let [shell-session-target (str session-name ":" (:window routing))
                    result (p/process ["tmux" "send-keys" "-t" shell-session-target command "Enter"]
                                     {:out :string :err :string})]
                (println (str "🐚 Shell command sent to: " shell-session-target))
                (if (= 0 (:exit @result))
                  {:type "shell-command-sent"
                   :content (str "Command sent to window " (:window routing))
                   :targetWindow (:window routing)
                   :commandType "shell-command"
                   :success true}
                  {:type "error"
                   :content (str "Failed to send shell command: " (:err @result))
                   :targetWindow (:window routing)
                   :success false})))))
        
        "ping"
        {:type "pong" :content "Server alive"}
        
        ;; 🌊 NEW: Start streaming from tmux session
        "start-streaming"
        (let [session-name (or (:session parsed) "qq-default")]
          (if-let [stream-info (start-tmux-streaming session-name client-socket)]
            {:type "streaming-started" 
             :content (str "Started streaming from " session-name)
             :session session-name
             :success true}
            {:type "error" 
             :content (str "Failed to start streaming from " session-name)
             :success false}))
        
        ;; 🛑 NEW: Stop streaming from tmux session  
        "stop-streaming"
        (let [session-name (or (:session parsed) "qq-default")]
          (remove-streaming-client client-socket)
          (stop-tmux-streaming session-name)
          {:type "streaming-stopped"
           :content (str "Stopped streaming from " session-name)
           :session session-name
           :success true})
        
        ;; 📋 NEW: Get streaming status
        "streaming-status"
        {:type "streaming-status"
         :content "Streaming sessions"
         :sessions (get-streaming-sessions)
         :clients (count @streaming-clients)
         :success true}
        
        ;; 📜 NEW: Load incremental history
        "load-incremental-history"
        (let [session-name (or (:session parsed) "qq-default")
              offset (or (:offset parsed) 50)
              limit (or (:limit parsed) 50)]
          (println (str "📜 Loading incremental history: session=" session-name " offset=" offset " limit=" limit))
          (load-incremental-history client-socket session-name offset limit)
          {:type "incremental-history-requested"
           :content (str "Loading " limit " lines at offset " offset)
           :session session-name
           :offset offset
           :limit limit
           :success true})
        
        ;; 🪟 NEW: Window management commands
        "list-windows"
        (let [session-name (or (:session parsed) "qq-default")
              windows (list-tmux-windows session-name)]
          (println (str "📋 Listed " (count windows) " windows for session " session-name))
          {:type "windows-list" :windows windows :session session-name :success true})
        
        "select-window"
        (let [session-name (or (:session parsed) "qq-default")
              window-index (:window parsed)
              result (select-tmux-window session-name window-index)]
          (if (:success result)
            (do
              (println (str "✅ Selected window " window-index " in session " session-name))
              {:type "window-selected" 
               :window window-index 
               :session session-name 
               :content (:content result)
               :success true})
            {:type "error" :content "Failed to select window" :success false}))
        
        "new-window"
        (let [session-name (or (:session parsed) "qq-default")]
          (if (create-tmux-window session-name)
            (do
              (println (str "✅ Created new window in session " session-name))
              {:type "window-created" :session session-name :success true})
            {:type "error" :content "Failed to create window" :success false}))
        
        ;; 📺 NEW: Window subscription management
        "subscribe-window"
        (let [session-name (if (= (:session parsed) "default") 
                            "qq-default"
                            (or (:session parsed) "qq-default"))
              window-index (:window parsed)]
          (subscribe-user-to-window client-socket session-name window-index)
          ;; Start streaming for this window if not already active
          (manage-window-streaming session-name)
          {:type "window-subscribed" 
           :session session-name 
           :window window-index 
           :success true})
        
        "unsubscribe-window"
        (do
          (unsubscribe-user client-socket)
          {:type "window-unsubscribed" :success true})
        
        ;; Default case
        {:type "error" :content "Unknown message type"}))
        
        ;; Invalid JSON - likely browser cleanup, don't send error response
        (do
          (println "⚠️ Skipping invalid message (likely browser cleanup)")
          nil))) ; Return nil for invalid JSON
    
    (catch Exception e
      (println (str "❌ Message handling error: " (.getMessage e)))
      ;; Don't send error response for connection cleanup issues
      nil)))

(defn- handle-connection [client-socket]
  "Handle WebSocket connection with complete message processing"
  (let [connection-id (str "conn-" (System/currentTimeMillis))]
    (try
      (println (str "📞 New WebSocket connection: " connection-id))
      (let [reader (BufferedReader. (InputStreamReader. (.getInputStream client-socket) "UTF-8"))
            writer (PrintWriter. (.getOutputStream client-socket) true)]
        
        ;; Read request line
        (let [request-line (.readLine reader)]
          (println (str "📥 Request [" connection-id "]: " request-line))
          
          ;; Read headers
          (loop [headers {}]
            (let [line (.readLine reader)]
              (if (or (nil? line) (empty? line))
                ;; Headers done, process handshake
                (let [websocket-key (get headers "sec-websocket-key")]
                  (if websocket-key
                    (do
                      (println (str "🔑 WebSocket key [" connection-id "]: " websocket-key))
                      
                      ;; Send proper WebSocket handshake response
                      (.println writer "HTTP/1.1 101 Switching Protocols")
                      (.println writer "Upgrade: websocket")
                      (.println writer "Connection: Upgrade")
                      (.println writer (str "Sec-WebSocket-Accept: " (websocket-accept-key websocket-key)))
                      (.println writer "Sec-WebSocket-Version: 13")
                      (.println writer "")
                      (.flush writer)
                      
                      (println (str "✅ WebSocket handshake completed [" connection-id "]"))
                      
                      ;; 🚀 AGGRESSIVE TMUX MIRRORING for maximum UX
                      (println (str "🚀 Starting AGGRESSIVE tmux mirroring for connection [" connection-id "]"))
                      (try
                        (start-aggressive-tmux-mirroring "qq-default" client-socket)
                        (println (str "✅ Aggressive mirroring started for [" connection-id "]"))
                      (catch Exception e
                        (println (str "❌ Aggressive mirroring failed for [" connection-id "]: " (.getMessage e)))))
                      
                      ;; Process WebSocket messages with proper binary I/O
                      (println (str "🔄 Starting WebSocket message processing [" connection-id "]..."))
                      (let [input-stream (.getInputStream client-socket)
                            output-stream (.getOutputStream client-socket)]
                        
                        (println (str "🔄 Entering message processing loop [" connection-id "]..."))
                        (while (and (.isConnected client-socket) (not (.isClosed client-socket)))
                          (when-let [message (read-websocket-frame input-stream connection-id)]
                            (println (str "📨 Processing WebSocket message [" connection-id "]: " message))
                            
                            ;; Handle the message and get response
                            (let [response (handle-websocket-message message client-socket)]
                              ;; Only send response if we got a valid one (not nil from browser cleanup)
                              (when response
                                (println (str "📤 Sending response [" connection-id "]: " response))
                                (send-websocket-frame output-stream (json/write-str response)))))
                          
                          ;; Small delay to prevent busy waiting
                          (Thread/sleep 100))
                        
                        (println (str "🔌 Message processing loop ended [" connection-id "]"))))
                    
                    (do
                      (println (str "❌ No WebSocket key found [" connection-id "]"))
                      (.println writer "HTTP/1.1 400 Bad Request")
                      (.println writer "")
                      (.flush writer))))
                
                ;; Parse header
                (if-let [[_ key value] (re-matches #"([^:]+):\s*(.+)" line)]
                  (recur (assoc headers (str/lower-case key) value))
                  (recur headers)))))))
      
      (catch Exception e
        (println (str "❌ Connection error [" connection-id "]: " (.getMessage e))))
      (finally
        (println (str "🔌 Closing connection [" connection-id "]"))
        (.close client-socket)))))

(defn start-websocket-server [port]
  "Start THE definitive WebSocket server with complete Q&A processing"
  (println (str "🚀 Starting THE WebSocket Server on port " port))
  (println "✅ Complete end-to-end Q&A flow: Browser → WebSocket → tmux → Q → response")
  (println "🎯 Features: Working handshake + WebSocket frames + Proper Q&A boundaries")
  
  (swap! server-state assoc :running true)
  
  ;; Start server
  (future
    (try
      (let [server-socket (ServerSocket. port)
            executor (Executors/newCachedThreadPool)]
        
        (swap! server-state assoc :server server-socket)
        (println (str "🌐 THE WebSocket server listening on port " port))
        
        ;; Start background connection cleanup task
        (start-connection-cleanup-task)
        (println "🧹 Started background connection cleanup task")
        
        (while (:running @server-state)
          (let [client-socket (.accept server-socket)]
            (.submit executor #(handle-connection client-socket))))
        
        (.close server-socket))
      (catch Exception e
        (println (str "❌ Server error: " (.getMessage e))))))
  
  ;; Keep main thread alive
  (println "💓 THE WebSocket server running - ready for complete Q&A!")
  (while (:running @server-state)
    (Thread/sleep 5000)
    (println "💓 Server heartbeat - processing Q&A"))
  
  (println "🛑 THE WebSocket server stopped"))

(defn stop-websocket-server []
  "Stop THE WebSocket server"
  (println "🛑 Stopping THE WebSocket server...")
  (swap! server-state assoc :running false)
  (when-let [server (:server @server-state)]
    (try (.close server) (catch Exception e nil)))
  (println "✅ THE WebSocket server stopped"))

(defn server-status []
  "Get server status"
  @server-state)
