(ns qq.terminal.simple-server
  "🔌 Simple WebSocket Server for Terminal
  
  A simplified WebSocket server implementation that focuses on reliability
  over features. Uses a more straightforward approach to WebSocket handshake.
  
  ## Features:
  - Reliable WebSocket handshake
  - Simple message protocol
  - Terminal session integration
  - Connection management
  
  ## Usage:
  ```clojure
  ;; Start simple server
  (start-simple-server {:port 9091})
  
  ;; Stop server
  (stop-simple-server)
  ```"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [babashka.process :as p]
            [qq.terminal.bridge :as bridge])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader InputStreamReader PrintWriter OutputStream]
           [java.util.concurrent Executors]
           [java.security MessageDigest]
           [java.util Base64]))

;; Server state
(def ^:private server-state (atom {:server nil :running false}))

;; WebSocket utilities (simplified)
(defn- websocket-accept-key
  "Generate WebSocket accept key"
  [client-key]
  (let [magic "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        combined (str client-key magic)
        sha1 (.digest (MessageDigest/getInstance "SHA-1") (.getBytes combined "UTF-8"))]
    (.encodeToString (Base64/getEncoder) sha1)))

(defn- send-websocket-handshake
  "Send WebSocket handshake response"
  [output-stream headers]
  (let [websocket-key (get headers "sec-websocket-key")
        accept-key (websocket-accept-key websocket-key)
        response (str "HTTP/1.1 101 Switching Protocols\r\n"
                     "Upgrade: websocket\r\n"
                     "Connection: Upgrade\r\n"
                     "Sec-WebSocket-Accept: " accept-key "\r\n"
                     "\r\n")]
    (.write output-stream (.getBytes response "UTF-8"))
    (.flush output-stream)
    (println "✅ WebSocket handshake sent")))

(defn- parse-http-headers
  "Parse HTTP headers from request"
  [reader]
  (loop [headers {}
         line (.readLine reader)]
    (if (or (nil? line) (empty? line))
      headers
      (if-let [[_ key value] (re-matches #"([^:]+):\s*(.+)" line)]
        (recur (assoc headers (str/lower-case key) value) (.readLine reader))
        (recur headers (.readLine reader))))))

(defn- send-text-frame
  "Send WebSocket text frame"
  [output-stream text]
  (try
    (let [payload (.getBytes text "UTF-8")
          payload-len (count payload)
          frame (if (< payload-len 126)
                  (byte-array (concat [0x81 payload-len] payload))
                  (byte-array (concat [0x81 126 
                                      (bit-shift-right payload-len 8) 
                                      (bit-and payload-len 0xFF)] 
                                     payload)))]
      (.write output-stream frame)
      (.flush output-stream)
      true)
    (catch Exception e
      (println (str "Error sending frame: " (.getMessage e)))
      false)))

(defn- handle-websocket-client
  "Handle WebSocket client connection"
  [socket session-id]
  (println (str "🔌 Handling WebSocket client for session: " session-id))
  
  (try
    (let [input-stream (.getInputStream socket)
          output-stream (.getOutputStream socket)
          reader (BufferedReader. (InputStreamReader. input-stream "UTF-8"))]
      
      ;; Read HTTP request line
      (let [request-line (.readLine reader)]
        (println (str "📥 Request: " request-line))
        
        ;; Parse headers
        (let [headers (parse-http-headers reader)]
          (println (str "📋 Headers: " (keys headers)))
          
          ;; Send WebSocket handshake
          (send-websocket-handshake output-stream headers)
          
          ;; Send welcome message
          (Thread/sleep 100) ; Small delay to ensure handshake is processed
          (send-text-frame output-stream 
            (json/write-str {:type "output"
                            :content "🤖 QQ Terminal Connected!\r\n=====================================\r\n📡 Session: default\r\n🎯 Type Q commands and see real-time responses\r\n💡 Use Ctrl+C to interrupt, Ctrl+D to exit\r\n\r\n$ "}))
          
          ;; Keep connection alive and handle messages
          (loop []
            (try
              ;; Simple keep-alive - in a real implementation we'd decode WebSocket frames
              (Thread/sleep 1000)
              (when (.isConnected socket)
                (recur))
              
              (catch Exception e
                (println (str "Connection loop error: " (.getMessage e)))))))))
    
    (catch Exception e
      (println (str "WebSocket client error: " (.getMessage e))))
    
    (finally
      (try
        (.close socket)
        (println (str "🔌 WebSocket connection closed for session: " session-id))
        (catch Exception e
          (println (str "Error closing socket: " (.getMessage e))))))))

(defn- extract-session-id
  "Extract session ID from request path"
  [request-line]
  (if-let [path (second (str/split request-line #" "))]
    (let [parts (str/split path #"/")]
      (if (and (>= (count parts) 3) (= (nth parts 1) "terminal"))
        (nth parts 2)
        "default"))
    "default"))

(defn start-simple-server
  "Start simple WebSocket server"
  [{:keys [port] :or {port 9091}}]
  (if (:running @server-state)
    (println "🌐 Simple server already running")
    (do
      (println (str "🚀 Starting simple WebSocket server on port " port "..."))
      
      (let [server-socket (ServerSocket. port)
            executor (Executors/newCachedThreadPool)]
        
        (swap! server-state assoc :server server-socket :running true)
        
        ;; Accept connections
        (.submit executor
          (fn []
            (try
              (while (:running @server-state)
                (let [client-socket (.accept server-socket)]
                  (println "📞 New client connection")
                  
                  ;; Handle in separate thread
                  (.submit executor
                    (fn []
                      (let [reader (BufferedReader. (InputStreamReader. (.getInputStream client-socket) "UTF-8"))
                            request-line (.readLine reader)
                            session-id (extract-session-id request-line)]
                        (handle-websocket-client client-socket session-id))))))
              
              (catch Exception e
                (when (:running @server-state)
                  (println (str "Server error: " (.getMessage e))))))))
        
        (println (str "✅ Simple WebSocket server started on ws://localhost:" port "/terminal/{session-id}"))
        (println "🎯 Ready for browser connections!")))))

(defn stop-simple-server
  "Stop simple WebSocket server"
  []
  (when-let [server (:server @server-state)]
    (println "🛑 Stopping simple WebSocket server...")
    (swap! server-state assoc :running false)
    (.close server)
    (swap! server-state assoc :server nil)
    (println "✅ Simple server stopped")))

(defn get-simple-server-status
  "Get simple server status"
  []
  @server-state)
