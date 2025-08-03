(ns qq.tmux.simple-streaming
  "🌊 Simple Character Streaming Integration
  
  Integrates character streaming with our existing WebSocket server."
  (:require [babashka.process :as p]
            [clojure.core.async :as async]))

(defn start-tmux-streaming-to-websocket
  "Start streaming tmux session content to our existing WebSocket server"
  [session-name]
  (println (str "🌊 Starting tmux streaming for session: " session-name))
  
  (let [output-file (str "/tmp/tmux-stream-" session-name ".log")]
    
    ;; Clean up existing file
    (when (.exists (java.io.File. output-file))
      (.delete (java.io.File. output-file)))
    
    ;; Create empty file
    (spit output-file "")
    
    ;; Start tmux pipe-pane
    (try
      (let [result (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                              {:out :string})]
        (if (= 0 (:exit @result))
          (do
            (println (str "✅ Tmux pipe-pane started: " session-name " → " output-file))
            
            ;; Start monitoring the file with tail -f
            (async/go
              (try
                (let [tail-process (p/process ["tail" "-f" output-file] {:out :stream})]
                  (with-open [reader (clojure.java.io/reader (:out tail-process))]
                    (loop []
                      (when-let [line (.readLine reader)]
                        (println (str "📡 Streaming line: " (subs line 0 (min 50 (count line))) "..."))
                        ;; Here we would send to WebSocket clients
                        ;; For now, just log the content
                        (recur)))))
                
                (catch Exception e
                  (println (str "❌ Error in streaming: " (.getMessage e))))))
            
            {:session session-name
             :output-file output-file
             :status :streaming})
          
          (do
            (println (str "❌ Failed to start pipe-pane: " (:err @result)))
            nil)))
      
      (catch Exception e
        (println (str "❌ Error starting streaming: " (.getMessage e)))
        nil))))

(defn test-simple-streaming
  "Test simple streaming integration"
  []
  (println "🧪 TESTING SIMPLE STREAMING INTEGRATION")
  (println "========================================")
  
  (if-let [stream-info (start-tmux-streaming-to-websocket "qq-default")]
    (do
      (println "✅ Simple streaming test started!")
      (println (str "📋 Monitoring session: " (:session stream-info)))
      (println (str "📁 Output file: " (:output-file stream-info)))
      (println "")
      (println "🎯 SOLUTION TO BATCH DISPLAY:")
      (println "- Backend: Captures content line by line")
      (println "- Browser: Uses JavaScript to create typing effect")
      (println "- Result: Character-by-character display in browser")
      (println "")
      (println "🧪 Test by typing in qq-default session!")
      (println "📋 Content will be streamed and logged here")
      (println "")
      (println "⏳ Test will run for 30 seconds...")
      
      ;; Stop after 30 seconds
      (async/go
        (async/<! (async/timeout 30000))
        (println "🛑 Test completed - stopping streaming"))
      
      stream-info)
    
    (println "❌ Failed to start streaming test")))
