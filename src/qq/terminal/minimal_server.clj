(ns qq.terminal.minimal-server
  "🔌 Minimal Working WebSocket Server
  
  A minimal WebSocket server that focuses on getting the handshake right.
  Based on the WebSocket RFC 6455 specification.
  
  ## Usage:
  ```clojure
  (start-minimal-server 9091)
  (stop-minimal-server)
  ```"
  (:require [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader InputStreamReader PrintWriter]
           [java.security MessageDigest]
           [java.util Base64]
           [java.util.concurrent Executors]))

;; Server state
(def ^:private server-atom (atom nil))

(defn- websocket-accept
  "Generate WebSocket accept key according to RFC 6455"
  [client-key]
  (let [magic "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        combined (str client-key magic)
        sha1 (.digest (MessageDigest/getInstance "SHA-1") (.getBytes combined "UTF-8"))]
    (.encodeToString (Base64/getEncoder) sha1)))

(defn- handle-websocket-handshake
  "Handle WebSocket handshake"
  [socket]
  (try
    (let [reader (BufferedReader. (InputStreamReader. (.getInputStream socket) "UTF-8"))
          writer (PrintWriter. (.getOutputStream socket) true)]
      
      ;; Read request line
      (let [request-line (.readLine reader)]
        (println (str "📥 Request: " request-line))
        
        ;; Read headers
        (loop [headers {}]
          (let [line (.readLine reader)]
            (if (or (nil? line) (empty? line))
              ;; Headers done, process handshake
              (let [websocket-key (get headers "sec-websocket-key")]
                (if websocket-key
                  (do
                    (println (str "🔑 WebSocket key: " websocket-key))
                    
                    ;; Send handshake response
                    (.println writer "HTTP/1.1 101 Switching Protocols")
                    (.println writer "Upgrade: websocket")
                    (.println writer "Connection: Upgrade")
                    (.println writer (str "Sec-WebSocket-Accept: " (websocket-accept websocket-key)))
                    (.println writer "")
                    (.flush writer)
                    
                    (println "✅ WebSocket handshake completed")
                    
                    ;; Send welcome message (simplified - just raw text for now)
                    (Thread/sleep 100)
                    (.print writer "🤖 QQ Terminal Connected!\n")
                    (.flush writer)
                    
                    ;; Keep connection alive
                    (loop []
                      (Thread/sleep 1000)
                      (when (.isConnected socket)
                        (recur))))
                  
                  (do
                    (println "❌ No WebSocket key found")
                    (.println writer "HTTP/1.1 400 Bad Request")
                    (.println writer "")
                    (.flush writer))))
              
              ;; Parse header
              (if-let [[_ key value] (re-matches #"([^:]+):\s*(.+)" line)]
                (recur (assoc headers (str/lower-case key) value))
                (recur headers)))))))
    
    (catch Exception e
      (println (str "Handshake error: " (.getMessage e))))
    
    (finally
      (.close socket))))

(defn start-minimal-server
  "Start minimal WebSocket server"
  [port]
  (when @server-atom
    (println "🛑 Stopping existing server...")
    (.close @server-atom)
    (reset! server-atom nil))
  
  (println (str "🚀 Starting minimal WebSocket server on port " port "..."))
  
  (let [server-socket (ServerSocket. port)
        executor (Executors/newCachedThreadPool)]
    
    (reset! server-atom server-socket)
    
    ;; Accept connections
    (.submit executor
      (fn []
        (try
          (while (not (.isClosed server-socket))
            (let [client-socket (.accept server-socket)]
              (println "📞 New connection")
              
              ;; Handle in separate thread
              (.submit executor
                (fn []
                  (handle-websocket-handshake client-socket)))))
          
          (catch Exception e
            (when (not (.isClosed server-socket))
              (println (str "Server error: " (.getMessage e))))))))
    
    (println (str "✅ Minimal server started on ws://localhost:" port))
    (println "🎯 Ready for WebSocket connections!")))

(defn stop-minimal-server
  "Stop minimal server"
  []
  (when-let [server @server-atom]
    (println "🛑 Stopping minimal server...")
    (.close server)
    (reset! server-atom nil)
    (println "✅ Minimal server stopped")))

(defn server-running?
  "Check if server is running"
  []
  (and @server-atom (not (.isClosed @server-atom))))
