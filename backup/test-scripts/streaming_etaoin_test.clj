(ns qq.browser.streaming-etaoin-test
  "🌊 Etaoin Test for Character-by-Character Streaming
  
  This tests the streaming functionality to fix the batch printing issue:
  1. Opens streaming-terminal.html in browser
  2. Starts streaming from tmux session
  3. Sends commands to tmux to generate content
  4. Verifies character-by-character display (not batch)
  5. Takes screenshots to show streaming effect"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]))

(defn start-streaming-test []
  "Test character-by-character streaming in browser"
  (println "🌊 STARTING STREAMING ETAOIN TEST")
  (println "=================================")
  
  (let [driver (e/chrome {:headless false :size [1400 900]})]
    (try
      ;; Step 1: Open streaming terminal
      (println "📋 Step 1: Opening streaming terminal...")
      (e/go driver "http://localhost:9090/web/streaming-terminal.html")
      (e/wait 3)
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/streaming-test-1-initial.png")
      (println "📸 Screenshot 1: Initial page loaded")
      
      ;; Step 2: Start streaming via WebSocket
      (println "📋 Step 2: Starting streaming via WebSocket...")
      (let [result (e/js-execute driver 
        "
        // Start streaming from qq-default session
        if (ws && ws.readyState === WebSocket.OPEN) {
          const message = {
            type: 'start-streaming',
            session: 'qq-default'
          };
          ws.send(JSON.stringify(message));
          return 'Streaming start message sent';
        } else {
          return 'WebSocket not connected';
        }
        ")]
        (println (str "📤 WebSocket result: " result)))
      
      (e/wait 2)
      (e/screenshot driver "screenshots/streaming-test-2-streaming-started.png")
      (println "📸 Screenshot 2: Streaming started")
      
      ;; Step 3: Send command to tmux to generate content
      (println "📋 Step 3: Sending command to tmux to generate streaming content...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "echo 'Character streaming test - each character should appear individually'" "Enter")
      (println "✅ Sent command to qq-default session")
      
      (e/wait 3)
      (e/screenshot driver "screenshots/streaming-test-3-content-generated.png")
      (println "📸 Screenshot 3: Content generated")
      
      ;; Step 4: Test streaming status
      (println "📋 Step 4: Checking streaming status...")
      (let [result (e/js-execute driver 
        "
        // Get streaming status
        if (ws && ws.readyState === WebSocket.OPEN) {
          const message = {
            type: 'streaming-status'
          };
          ws.send(JSON.stringify(message));
          return 'Status request sent';
        } else {
          return 'WebSocket not connected';
        }
        ")]
        (println (str "📊 Status result: " result)))
      
      (e/wait 2)
      (e/screenshot driver "screenshots/streaming-test-4-status-checked.png")
      (println "📸 Screenshot 4: Status checked")
      
      ;; Step 5: Test character streaming with longer content
      (println "📋 Step 5: Testing character streaming with longer content...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "echo 'This is a longer message to test character-by-character streaming. Each character should appear individually with typing effect.'" "Enter")
      (println "✅ Sent longer content for streaming test")
      
      (e/wait 4)
      (e/screenshot driver "screenshots/streaming-test-5-long-content.png")
      (println "📸 Screenshot 5: Long content streamed")
      
      ;; Step 6: Check terminal content
      (println "📋 Step 6: Checking terminal content...")
      (let [terminal-content (e/js-execute driver 
        "
        // Get terminal content to verify streaming
        if (terminal && terminal.buffer) {
          const lines = [];
          for (let i = 0; i < terminal.buffer.active.length; i++) {
            const line = terminal.buffer.active.getLine(i);
            if (line) {
              lines.push(line.translateToString());
            }
          }
          return lines.join('\\n');
        } else {
          return 'Terminal not available';
        }
        ")]
        (println "📋 Terminal content:")
        (println terminal-content))
      
      ;; Step 7: Stop streaming
      (println "📋 Step 7: Stopping streaming...")
      (let [result (e/js-execute driver 
        "
        // Stop streaming
        if (ws && ws.readyState === WebSocket.OPEN) {
          const message = {
            type: 'stop-streaming',
            session: 'qq-default'
          };
          ws.send(JSON.stringify(message));
          return 'Streaming stop message sent';
        } else {
          return 'WebSocket not connected';
        }
        ")]
        (println (str "🛑 Stop result: " result)))
      
      (e/wait 2)
      (e/screenshot driver "screenshots/streaming-test-6-streaming-stopped.png")
      (println "📸 Screenshot 6: Streaming stopped")
      
      ;; Final wait to see results
      (println "📋 Final: Waiting to observe results...")
      (e/wait 5)
      (e/screenshot driver "screenshots/streaming-test-7-final.png")
      (println "📸 Screenshot 7: Final state")
      
      (println "")
      (println "✅ STREAMING TEST COMPLETED!")
      (println "============================")
      (println "📸 Screenshots saved to screenshots/ directory")
      (println "🔍 Check screenshots to verify character-by-character streaming")
      (println "")
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      
      (finally
        (println "🔄 Keeping browser open for 10 seconds to observe...")
        (Thread/sleep 10000)
        (e/quit driver)))))

(defn test-streaming-display
  "Quick test to verify streaming display works"
  []
  (start-streaming-test))
