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
  "Clean streaming content by removing ANSI escape sequences and spinner chars"
  [content]
  (-> content
      ;; Remove ANSI escape sequences
      (str/replace #"\u001B\[[0-9;]*[mK]" "")
      ;; Remove spinner characters (Braille patterns)
      (str/replace #"[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]" "")
      ;; Remove carriage returns that cause overwriting
      (str/replace #"\r" "")
      ;; Remove excessive dots and spaces
      (str/replace #"\.{3,}" "...")
      ;; Clean up multiple spaces
      (str/replace #" {2,}" " ")
      ;; Remove empty lines
      (str/replace #"\n\s*\n" "\n")))

;; 🚀 AGGRESSIVE TMUX MIRRORING FUNCTIONS

;; Store active WebSocket clients for mirroring
(def streaming-clients (atom #{}))

(defn add-streaming-client [client-socket]
  "Add a client socket to streaming clients"
  (swap! streaming-clients conj client-socket)
  (println (str "📡 Added mirroring client. Total clients: " (count @streaming-clients))))

(defn remove-streaming-client [client-socket]
  "Remove a client socket from streaming clients"
  (swap! streaming-clients disj client-socket)
  (println (str "📡 Removed mirroring client. Total clients: " (count @streaming-clients))))

(defn broadcast-to-streaming-clients [message]
  "Broadcast message to all streaming clients"
  (doseq [client-socket @streaming-clients]
    (try
      (let [output-stream (.getOutputStream client-socket)
            json-message (json/write-str message)]
        (send-websocket-frame output-stream json-message))
      (catch Exception e
        (println (str "❌ Error broadcasting to client: " (.getMessage e)))
        ;; Remove failed client
        (remove-streaming-client client-socket)))))

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

(defn sync-full-tmux-content
  "Send the entire tmux content to a WebSocket client"
  [client-socket session-name]
  (println (str "🔄 Syncing full tmux content to client"))
  (try
    (let [full-content (capture-full-tmux-history session-name)
          cleaned-content (clean-streaming-content full-content)]
      (when (not (str/blank? cleaned-content))
        (let [message {:type "tmux-full-sync"
                       :content cleaned-content
                       :session session-name
                       :timestamp (System/currentTimeMillis)}]
          (println (str "📡 Sending full sync: " (count (str/split-lines cleaned-content)) " lines"))
          ;; Send directly to this client
          (let [output-stream (.getOutputStream client-socket)
                json-message (json/write-str message)]
            (send-websocket-frame output-stream json-message)))))
    (catch Exception e
      (println (str "❌ Error syncing full content: " (.getMessage e))))))

(defn start-aggressive-file-monitoring
  "Monitor file changes for aggressive real-time mirroring"
  [output-file session-name]
  (println (str "👁️ Starting aggressive file monitoring: " output-file))
  
  (async/go
    (try
      (let [tail-process (p/process ["tail" "-f" output-file] {:out :stream})]
        (with-open [reader (io/reader (:out tail-process))]
          (loop []
            (when-let [line (.readLine reader)]
              ;; For aggressive mirroring, send ALL content (minimal filtering)
              (let [cleaned-content (-> line
                                        ;; Only remove the most problematic chars
                                        (str/replace #"[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]" "")
                                        (str/replace #"\r" ""))]
                (when (not (str/blank? cleaned-content))
                  (let [message {:type "tmux-realtime"
                                 :content cleaned-content
                                 :session session-name
                                 :timestamp (System/currentTimeMillis)}]
                    (println (str "📡 Aggressive mirror: " (subs cleaned-content 0 (min 80 (count cleaned-content))) "..."))
                    (broadcast-to-streaming-clients message))))
              (recur)))))
      
      (catch Exception e
        (println (str "❌ Error in aggressive file monitoring: " (.getMessage e)))))))

(defn start-aggressive-tmux-mirroring
  "Start aggressive tmux mirroring with full history sync"
  [session-name client-socket]
  (println (str "🚀 Starting AGGRESSIVE tmux mirroring for: " session-name))
  
  ;; Step 1: Sync existing content immediately
  (sync-full-tmux-content client-socket session-name)
  
  ;; Step 2: Start real-time streaming
  (add-streaming-client client-socket)
  
  (let [output-file (str "/tmp/tmux-mirror-" session-name ".log")]
    
    ;; Clean up existing file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty file
    (spit output-file "")
    
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

(defn start-tmux-streaming
  "Start streaming from a tmux session to WebSocket clients"
  [session-name client-socket]
  (println (str "🌊 Starting tmux streaming for session: " session-name))
  
  ;; Add client to streaming clients
  (add-streaming-client client-socket)
  
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
    (let [parsed (json/read-str message-data :key-fn keyword)
          command (:content parsed)
          session-id (or (:session parsed) "qq-default")]
      
      (println (str "📨 WebSocket Message: " parsed))
      
      (case (:type parsed)
        "command"
        (let [result (process-qa-command command session-id)]
          (if (:success result)
            {:type "output" :content (:output result) :success true}
            {:type "error" :content (:error result) :success false}))
        
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
        
        ;; Default case
        {:type "error" :content "Unknown message type"}))
    
    (catch Exception e
      (println (str "❌ Message handling error: " (.getMessage e)))
      {:type "error" :content (str "Message error: " (.getMessage e))})))

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
                              (println (str "📤 Sending response [" connection-id "]: " response))
                              (send-websocket-frame output-stream (json/write-str response))))
                          
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
