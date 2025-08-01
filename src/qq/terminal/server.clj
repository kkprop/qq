(ns qq.terminal.server
  "🔌 QQ Terminal WebSocket Server
  
  Real-time WebSocket server for browser-based terminal experience.
  Handles bidirectional communication between browser terminal and Q sessions.
  
  ## Features:
  - WebSocket server for real-time I/O
  - Session multiplexing (multiple terminals)
  - tmux integration for Q session communication
  - Command forwarding and output streaming
  - Connection management and auto-reconnect
  
  ## Usage:
  ```clojure
  ;; Start WebSocket server
  (start-terminal-server {:port 9091})
  
  ;; Stop server
  (stop-terminal-server)
  ```"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [babashka.process :as p]
            [qq.session.manager :as session-mgr])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader InputStreamReader PrintWriter]
           [java.util.concurrent Executors]
           [java.security MessageDigest]
           [java.util Base64]))

;; Server state
(def ^:private server-state (atom {:server nil
                                   :connections {}
                                   :sessions {}}))

;; WebSocket utilities
(defn- websocket-key-response
  "Generate WebSocket accept key from client key"
  [client-key]
  (let [magic-string "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        combined (str client-key magic-string)
        sha1 (.digest (MessageDigest/getInstance "SHA-1") (.getBytes combined))
        encoded (.encodeToString (Base64/getEncoder) sha1)]
    encoded))

(defn- parse-websocket-request
  "Parse WebSocket handshake request"
  [request-lines]
  (let [headers (into {} 
                      (map (fn [line]
                             (let [[k v] (str/split line #": " 2)]
                               [(str/lower-case k) v]))
                           (rest request-lines)))
        path (-> (first request-lines)
                 (str/split #" ")
                 second)]
    {:path path
     :headers headers
     :websocket-key (get headers "sec-websocket-key")}))

(defn- send-websocket-handshake
  "Send WebSocket handshake response"
  [writer websocket-key]
  (let [accept-key (websocket-key-response websocket-key)]
    (.println writer "HTTP/1.1 101 Switching Protocols")
    (.println writer "Upgrade: websocket")
    (.println writer "Connection: Upgrade")
    (.println writer (str "Sec-WebSocket-Accept: " accept-key))
    (.println writer "")
    (.flush writer)))

(defn- decode-websocket-frame
  "Decode WebSocket frame (simplified for text frames)"
  [input-stream]
  (try
    (let [first-byte (.read input-stream)
          second-byte (.read input-stream)]
      (when (and (>= first-byte 0) (>= second-byte 0))
        (let [fin (bit-test first-byte 7)
              opcode (bit-and first-byte 0x0F)
              masked (bit-test second-byte 7)
              payload-len (bit-and second-byte 0x7F)]
          
          (when (and fin (= opcode 1) masked) ; Text frame, masked
            (let [actual-len (cond
                               (< payload-len 126) payload-len
                               (= payload-len 126) (+ (* (.read input-stream) 256) (.read input-stream))
                               :else nil) ; Extended length not implemented
                  mask-key (byte-array 4)]
              
              (when actual-len
                (.read input-stream mask-key)
                (let [payload (byte-array actual-len)]
                  (.read input-stream payload)
                  
                  ;; Unmask payload
                  (dotimes [i actual-len]
                    (aset payload i (byte (bit-xor (aget payload i) (aget mask-key (mod i 4))))))
                  
                  (String. payload "UTF-8"))))))))
    (catch Exception e
      (println (str "Error decoding WebSocket frame: " (.getMessage e)))
      nil)))

(defn- encode-websocket-frame
  "Encode text as WebSocket frame"
  [text]
  (let [payload (.getBytes text "UTF-8")
        payload-len (count payload)]
    (cond
      (< payload-len 126)
      (byte-array (concat [0x81 payload-len] payload))
      
      (< payload-len 65536)
      (byte-array (concat [0x81 126 (bit-shift-right payload-len 8) (bit-and payload-len 0xFF)] payload))
      
      :else
      (throw (Exception. "Payload too large")))))

;; Session management
(defn- get-session-info
  "Get information about a Q session"
  [session-id]
  (let [tmux-session (str "qq-" session-id)]
    (try
      (let [result (p/shell {:out :string} "tmux" "list-sessions" "-F" "#{session_name}:#{session_attached}")
            lines (str/split-lines (:out result))
            session-line (first (filter #(str/starts-with? % tmux-session) lines))]
        
        (if session-line
          {:exists true
           :name tmux-session
           :attached (str/includes? session-line ":1")
           :status "active"}
          {:exists false
           :name tmux-session
           :status "not-found"}))
      
      (catch Exception e
        {:exists false
         :error (.getMessage e)
         :status "error"}))))

(defn- send-command-to-session
  "Send command to Q session via tmux"
  [session-id command]
  (let [tmux-session (str "qq-" session-id)]
    (try
      (println (str "📤 Sending to " tmux-session ": " command))
      (p/shell "tmux" "send-keys" "-t" tmux-session command "Enter")
      {:success true}
      
      (catch Exception e
        (println (str "❌ Error sending command: " (.getMessage e)))
        {:success false :error (.getMessage e)}))))

(defn- capture-session-output
  "Capture recent output from Q session"
  [session-id]
  (let [tmux-session (str "qq-" session-id)]
    (try
      (let [result (p/shell {:out :string} "tmux" "capture-pane" "-t" tmux-session "-p")]
        {:success true :output (:out result)})
      
      (catch Exception e
        {:success false :error (.getMessage e)}))))

;; WebSocket message handling
(defn- send-websocket-message
  "Send JSON message through WebSocket"
  [output-stream message]
  (try
    (let [json-str (json/write-str message)
          frame (encode-websocket-frame json-str)]
      (.write output-stream frame)
      (.flush output-stream)
      true)
    (catch Exception e
      (println (str "Error sending WebSocket message: " (.getMessage e)))
      false)))

(defn- handle-terminal-message
  "Handle incoming terminal message"
  [session-id message output-stream]
  (try
    (let [data (json/read-str message :key-fn keyword)]
      (case (:type data)
        "command"
        (do
          (println (str "🎯 Command from " session-id ": " (:content data)))
          (let [result (send-command-to-session session-id (:content data))]
            (if (:success result)
              (do
                ;; Wait a bit for command to execute
                (Thread/sleep 500)
                ;; Capture and send output
                (let [output-result (capture-session-output session-id)]
                  (when (:success output-result)
                    (send-websocket-message output-stream
                      {:type "output"
                       :content (str (:output output-result) "$ ")}))))
              (send-websocket-message output-stream
                {:type "output"
                 :content (str "❌ Error: " (:error result) "\r\n$ ")}))))
        
        "interrupt"
        (do
          (println (str "🛑 Interrupt signal for " session-id))
          (p/shell "tmux" "send-keys" "-t" (str "qq-" session-id) "C-c")
          (send-websocket-message output-stream
            {:type "output"
             :content "\r\n$ "}))
        
        "eof"
        (do
          (println (str "📤 EOF signal for " session-id))
          (p/shell "tmux" "send-keys" "-t" (str "qq-" session-id) "C-d"))
        
        (println (str "❓ Unknown message type: " (:type data)))))
    
    (catch Exception e
      (println (str "Error handling terminal message: " (.getMessage e))))))

;; Connection handling
(defn- handle-websocket-connection
  "Handle individual WebSocket connection"
  [socket session-id]
  (println (str "🔌 New WebSocket connection for session: " session-id))
  
  (try
    (let [input-stream (.getInputStream socket)
          output-stream (.getOutputStream socket)
          reader (BufferedReader. (InputStreamReader. input-stream))
          writer (PrintWriter. output-stream true)]
      
      ;; Read HTTP request
      (let [request-lines (loop [lines []]
                            (let [line (.readLine reader)]
                              (if (or (nil? line) (empty? line))
                                lines
                                (recur (conj lines line)))))
            request (parse-websocket-request request-lines)]
        
        ;; Send WebSocket handshake
        (send-websocket-handshake writer (:websocket-key request))
        
        ;; Send initial session info
        (let [session-info (get-session-info session-id)]
          (send-websocket-message output-stream
            {:type "session_info"
             :info (if (:exists session-info)
                     (str "Connected to " (:name session-info) " (" (:status session-info) ")")
                     (str "Session " session-id " not found - will create on first command"))}))
        
        ;; Handle WebSocket messages
        (loop []
          (when-let [message (decode-websocket-frame input-stream)]
            (handle-terminal-message session-id message output-stream)
            (recur)))))
    
    (catch Exception e
      (println (str "WebSocket connection error: " (.getMessage e))))
    
    (finally
      (.close socket)
      (println (str "🔌 WebSocket connection closed for session: " session-id)))))

(defn- extract-session-from-path
  "Extract session ID from WebSocket path"
  [path]
  (let [parts (str/split path #"/")]
    (if (and (>= (count parts) 3) (= (nth parts 1) "terminal"))
      (nth parts 2)
      "default")))

;; Server management
(defn start-terminal-server
  "Start WebSocket server for terminal connections"
  [{:keys [port] :or {port 9091}}]
  (if (:server @server-state)
    (println "🌐 Terminal server already running")
    (do
      (println (str "🚀 Starting terminal WebSocket server on port " port "..."))
      
      (let [server-socket (ServerSocket. port)
            executor (Executors/newCachedThreadPool)]
        
        (swap! server-state assoc :server server-socket)
        
        ;; Accept connections in background
        (.submit executor
          (fn []
            (try
              (while (not (.isClosed server-socket))
                (let [client-socket (.accept server-socket)
                      client-input (.getInputStream client-socket)
                      reader (BufferedReader. (InputStreamReader. client-input))]
                  
                  ;; Read first line to get path
                  (let [first-line (.readLine reader)
                        path (when first-line
                               (-> first-line
                                   (str/split #" ")
                                   second))
                        session-id (extract-session-from-path path)]
                    
                    ;; Handle connection in separate thread
                    (.submit executor
                      (fn []
                        (handle-websocket-connection client-socket session-id))))))
              
              (catch Exception e
                (when (not (.isClosed server-socket))
                  (println (str "Server error: " (.getMessage e))))))))
        
        (println (str "✅ Terminal WebSocket server started on ws://localhost:" port "/terminal/{session-id}"))
        (println "🎯 Ready for browser terminal connections!")
        
        ;; Keep the main thread alive by blocking on server socket
        ;; This prevents the bb task from exiting immediately
        (try
          (while (not (.isClosed server-socket))
            (Thread/sleep 1000)) ; Check every second
        (catch InterruptedException e
          (println "🛑 Server interrupted, shutting down...")
          (.close server-socket)))))))

(defn stop-terminal-server
  "Stop WebSocket server"
  []
  (when-let [server (:server @server-state)]
    (println "🛑 Stopping terminal WebSocket server...")
    (.close server)
    (swap! server-state assoc :server nil)
    (println "✅ Terminal server stopped")))

(defn get-server-status
  "Get current server status"
  []
  (let [state @server-state]
    {:running (some? (:server state))
     :connections (count (:connections state))
     :sessions (keys (:sessions state))}))
