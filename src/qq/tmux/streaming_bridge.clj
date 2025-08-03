(ns qq.tmux.streaming-bridge
  "🌊 Character-by-Character Streaming Bridge
  
  This creates a true character-by-character streaming experience
  from tmux to browser, preserving the real-time typing effect."
  (:require [clojure.core.async :as async]
            [babashka.process :as p]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def streaming-state (atom {:running false 
                            :tmux-streams {}
                            :websocket-clients #{}}))

(defn start-character-streaming
  "Start character-by-character streaming from tmux session"
  [session-name output-chan]
  (println (str "🌊 Starting character-by-character streaming for: " session-name))
  
  (let [output-file (str "/tmp/tmux-stream-" session-name ".log")]
    
    ;; Clean up any existing output file
    (when (.exists (File. output-file))
      (.delete (File. output-file)))
    
    ;; Create empty output file
    (spit output-file "")
    
    ;; Start tmux pipe-pane
    (try
      (p/process ["tmux" "pipe-pane" "-t" session-name "-o" (str "cat >> " output-file)] 
                 {:out :string})
      (println (str "✅ Tmux pipe-pane started for character streaming"))
      
      ;; Use tail -f with character-by-character reading
      (async/go
        (with-open [reader (io/reader (:in (p/process ["tail" "-f" output-file] {:out :stream})))]
          (try
            (loop []
              (when-let [char (.read reader)]
                (when (>= char 0) ; Valid character
                  (let [char-str (str (char char))]
                    ;; Send each character immediately
                    (async/>! output-chan {:type "character"
                                           :content char-str
                                           :timestamp (System/currentTimeMillis)
                                           :session session-name})
                    (recur)))))
            
            (catch Exception e
              (println (str "❌ Error in character streaming: " (.getMessage e))))
            
            (finally
              (println "🛑 Character streaming stopped")
              (async/close! output-chan)))))
      
      {:session session-name
       :output-file output-file
       :output-chan output-chan})
      
      (catch Exception e
        (println (str "❌ Failed to start character streaming: " (.getMessage e)))
        nil))))

(defn create-streaming-websocket-bridge
  "Create a bridge that streams characters in real-time to WebSocket"
  [session-name websocket-handler]
  (println (str "🌊 Creating character streaming bridge for: " session-name))
  
  (let [output-chan (async/chan 1000)] ; Larger buffer for characters
    
    ;; Start character streaming
    (if-let [stream-info (start-character-streaming session-name output-chan)]
      (do
        ;; Process character stream
        (async/go-loop []
          (when-let [char-data (async/<! output-chan)]
            ;; Send character immediately to WebSocket
            (when websocket-handler
              (websocket-handler char-data))
            
            ;; Also log for debugging (but don't spam)
            (when (= 0 (mod (System/currentTimeMillis) 100))
              (println (str "📡 Streaming char: " (pr-str (:content char-data)))))
            
            (recur)))
        
        (println "✅ Character streaming bridge active")
        stream-info)
      
      (println "❌ Failed to create streaming bridge"))))

(defn test-character-streaming
  "Test character-by-character streaming"
  []
  (println "🧪 TESTING CHARACTER-BY-CHARACTER STREAMING")
  (println "============================================")
  
  (let [session-name "qq-default"
        char-count (atom 0)
        
        ;; Mock WebSocket handler for testing
        websocket-handler (fn [char-data]
                            (swap! char-count inc)
                            (when (= 0 (mod @char-count 10))
                              (println (str "📊 Streamed " @char-count " characters so far..."))))]
    
    ;; Create streaming bridge
    (if-let [bridge-info (create-streaming-websocket-bridge session-name websocket-handler)]
      (do
        (println "✅ Character streaming test started!")
        (println "📋 Type in qq-default session to see character-by-character streaming")
        (println "📊 Characters will be counted and logged")
        (println "")
        (println "🧪 Test command to try:")
        (println "tmux send-keys -t qq-default \"echo 'Character streaming test'\" Enter")
        (println "")
        (println "⏳ Test will run for 30 seconds...")
        
        ;; Run test for 30 seconds
        (async/go
          (async/<! (async/timeout 30000))
          (println (str "🎯 Test completed! Total characters streamed: " @char-count))
          (println "🛑 Stopping character streaming test"))
        
        bridge-info)
      
      (println "❌ Failed to start character streaming test"))))
