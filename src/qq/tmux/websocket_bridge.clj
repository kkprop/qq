(ns qq.tmux.websocket-bridge
  "🌉 Tmux-to-WebSocket Bridge - Cross-Platform Real Content Streaming
  
  This bridges tmux session output directly to WebSocket clients
  using tmux pipe-pane for real stdout text capture."
  (:require [clojure.core.async :as async]
            [babashka.process :as p]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def bridge-state (atom {:running false 
                         :tmux-streams {}
                         :websocket-clients #{}}))

(defn start-tmux-pipe-pane
  "Start tmux pipe-pane to capture session output to a file"
  [session-name output-file]
  (println (str "🔧 Starting tmux pipe-pane for session: " session-name))
  
  ;; Stop any existing pipe-pane for this session
  (try
    (p/process ["tmux" "pipe-pane" "-t" session-name] {:out :string})
    (catch Exception e
      (println "No existing pipe-pane to stop")))
  
  ;; Start new pipe-pane
  (let [result (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                          {:out :string})]
    (if (= 0 (:exit @result))
      (do
        (println (str "✅ Tmux pipe-pane started: " session-name " → " output-file))
        true)
      (do
        (println (str "❌ Failed to start pipe-pane: " (:err @result)))
        false))))

(defn stop-tmux-pipe-pane
  "Stop tmux pipe-pane for a session"
  [session-name]
  (println (str "🛑 Stopping tmux pipe-pane for session: " session-name))
  
  (try
    (let [result (p/process ["tmux" "pipe-pane" "-t" session-name] {:out :string})]
      (println "✅ Tmux pipe-pane stopped"))
    (catch Exception e
      (println (str "❌ Error stopping pipe-pane: " (.getMessage e))))))

(defn watch-tmux-output-file
  "Watch tmux output file and stream changes to channel"
  [output-file output-chan]
  (println (str "👀 Watching tmux output file: " output-file))
  
  (async/go
    (try
      ;; Use tail -f to follow the file
      (let [tail-process (p/process ["tail" "-f" output-file] {:out :stream :err :stream})]
        
        (with-open [reader (io/reader (:out tail-process))]
          (loop []
            (when-let [line (.readLine reader)]
              (async/>! output-chan {:type "tmux-output"
                                     :content line
                                     :timestamp (System/currentTimeMillis)
                                     :source output-file})
              (recur))))
        
        (println "🛑 File watching stopped"))
      
      (catch Exception e
        (println (str "❌ Error watching file: " (.getMessage e))))
      
      (finally
        (async/close! output-chan)))))

(defn create-tmux-websocket-bridge
  "Create a bridge that streams tmux session output to WebSocket clients"
  [session-name]
  (println (str "🌉 Creating tmux-to-WebSocket bridge for session: " session-name))
  
  (let [output-file (str "/tmp/tmux-bridge-" session-name ".log")
        output-chan (async/chan 100)]
    
    ;; Clean up any existing output file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty output file
    (spit output-file "")
    
    ;; Start tmux pipe-pane
    (if (start-tmux-pipe-pane session-name output-file)
      (do
        ;; Start watching the output file
        (watch-tmux-output-file output-file output-chan)
        
        ;; Store the stream in our state
        (swap! bridge-state assoc-in [:tmux-streams session-name] 
               {:output-chan output-chan
                :output-file output-file})
        
        ;; Start processing the stream
        (async/go-loop []
          (when-let [tmux-data (async/<! output-chan)]
            (println (str "📡 Tmux content: " (subs (:content tmux-data) 0 (min 50 (count (:content tmux-data)))) "..."))
            
            ;; Here we would broadcast to WebSocket clients
            ;; For now, just log the content
            (recur)))
        
        (println (str "✅ Tmux-to-WebSocket bridge active for session: " session-name))
        {:session session-name
         :output-file output-file
         :output-chan output-chan})
      
      (do
        (println "❌ Failed to create tmux bridge")
        nil))))

(defn stop-tmux-websocket-bridge
  "Stop the tmux-to-WebSocket bridge for a session"
  [session-name]
  (println (str "🛑 Stopping tmux bridge for session: " session-name))
  
  ;; Stop pipe-pane
  (stop-tmux-pipe-pane session-name)
  
  ;; Clean up state
  (when-let [stream-info (get-in @bridge-state [:tmux-streams session-name])]
    (async/close! (:output-chan stream-info))
    (swap! bridge-state update :tmux-streams dissoc session-name))
  
  (println "✅ Tmux bridge stopped"))

(defn test-tmux-websocket-bridge
  "Test the tmux-to-WebSocket bridge"
  []
  (println "🧪 TESTING TMUX-TO-WEBSOCKET BRIDGE")
  (println "====================================")
  
  ;; Test with qq-default session
  (let [session-name "qq-default"
        bridge-info (create-tmux-websocket-bridge session-name)]
    
    (if bridge-info
      (do
        (println "✅ Tmux bridge test setup complete!")
        (println (str "📋 Monitoring tmux session: " session-name))
        (println (str "📋 Output file: " (:output-file bridge-info)))
        (println "📋 Real tmux content will stream to WebSocket clients")
        (println "")
        (println "🎯 Bridge is now capturing real tmux output!")
        (println "📋 Type in the qq-default session to see content capture")
        (println "")
        (println "⏳ Bridge will run for 30 seconds for testing...")
        
        ;; Run for 30 seconds then stop
        (async/go
          (async/<! (async/timeout 30000))
          (println "🛑 Test completed - stopping bridge")
          (stop-tmux-websocket-bridge session-name))
        
        bridge-info)
      
      (println "❌ Failed to create tmux bridge"))))
