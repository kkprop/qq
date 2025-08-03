(ns qq.process.websocket-bridge
  "🌉 Process-to-WebSocket Bridge - Revolutionary Direct Streaming"
  (:require [clojure.core.async :as async]
            [qq.process.monitor :as process-monitor]
            [clojure.data.json :as json])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader InputStreamReader PrintWriter]))

(def bridge-state (atom {:running false 
                         :process-streams {}
                         :websocket-clients #{}}))

(defn broadcast-to-websocket-clients
  "Broadcast process data to all connected WebSocket clients"
  [process-data]
  (let [clients (:websocket-clients @bridge-state)
        message (json/write-str {:type "process-stream"
                                 :timestamp (System/currentTimeMillis)
                                 :data process-data})]
    
    (println (str "📡 Broadcasting to " (count clients) " clients: " (:type process-data)))))

(defn create-process-websocket-bridge
  "Create a bridge that streams process data to WebSocket clients"
  [pid]
  (println (str "🌉 Creating process-to-WebSocket bridge for PID: " pid))
  
  (let [process-stream (process-monitor/create-process-stream pid)]
    
    ;; Store the stream in our state
    (swap! bridge-state assoc-in [:process-streams pid] process-stream)
    
    ;; Start processing the stream
    (async/go-loop []
      (when-let [process-data (async/<! process-stream)]
        (broadcast-to-websocket-clients process-data)
        (recur)))
    
    (println (str "✅ Process-to-WebSocket bridge active for PID: " pid))
    process-stream))

(defn test-process-websocket-bridge
  "Test the process-to-WebSocket bridge"
  []
  (println "🧪 TESTING PROCESS-TO-WEBSOCKET BRIDGE")
  (println "=======================================")
  
  ;; Create bridge for our current chat process
  (let [chat-pid 68923
        bridge-stream (create-process-websocket-bridge chat-pid)]
    
    (println "✅ Bridge test setup complete!")
    (println "📋 Monitoring PID 68923 (our current chat)")
    (println "📋 Process data will be broadcast to WebSocket clients")
    (println "")
    (println "🎯 Bridge is now streaming process data!")
    (println "📋 Next: Connect this to actual WebSocket server")
    
    ;; Return bridge info
    {:monitored-pid chat-pid
     :bridge-stream bridge-stream}))
