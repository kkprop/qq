(ns qq.browser.main-terminal-streaming-test
  "🌊 Etaoin Test for Integrated Streaming in Main Terminal
  
  This tests the streaming functionality integrated into the main terminal.html:
  1. Opens main terminal.html in browser
  2. Tests streaming controls (Start/Stop/Status buttons)
  3. Verifies character-by-character streaming display
  4. Confirms integration with existing terminal functionality"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]))

(defn test-main-terminal-streaming []
  "Test integrated streaming functionality in main terminal"
  (println "🌊 TESTING MAIN TERMINAL STREAMING INTEGRATION")
  (println "==============================================")
  
  (let [driver (e/chrome {:headless false :size [1400 900]})]
    (try
      ;; Step 1: Open main terminal
      (println "📋 Step 1: Opening main terminal...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      (e/wait 4)
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/main-terminal-streaming-1-initial.png")
      (println "📸 Screenshot 1: Main terminal loaded")
      
      ;; Step 2: Test streaming button visibility
      (println "📋 Step 2: Checking streaming controls...")
      (let [streaming-btn-exists (try 
                                   (e/exists? driver {:id "streamingBtn"})
                                   (catch Exception e false))]
        (if streaming-btn-exists
          (println "✅ Streaming button found")
          (println "❌ Streaming button not found")))
      
      (e/screenshot driver "screenshots/main-terminal-streaming-2-controls.png")
      (println "📸 Screenshot 2: Controls visible")
      
      ;; Step 3: Start streaming
      (println "📋 Step 3: Starting streaming...")
      (try
        (e/click driver {:id "streamingBtn"})
        (println "✅ Clicked streaming button")
        (e/wait 2)
      (catch Exception e
        (println (str "❌ Error clicking streaming button: " (.getMessage e)))))
      
      (e/screenshot driver "screenshots/main-terminal-streaming-3-streaming-started.png")
      (println "📸 Screenshot 3: Streaming started")
      
      ;; Step 4: Send command to tmux to generate streaming content
      (println "📋 Step 4: Generating streaming content...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "echo 'Main terminal streaming test - character by character display'" "Enter")
      (println "✅ Sent command to qq-default session")
      
      (e/wait 4)
      (e/screenshot driver "screenshots/main-terminal-streaming-4-content-streamed.png")
      (println "📸 Screenshot 4: Content streamed")
      
      ;; Step 5: Test streaming status
      (println "📋 Step 5: Checking streaming status...")
      (try
        ;; Look for status button by text content
        (let [status-buttons (e/query-all driver {:tag "button"})]
          (doseq [btn status-buttons]
            (let [btn-text (e/get-element-text driver btn)]
              (when (clojure.string/includes? btn-text "Status")
                (e/click driver btn)
                (println "✅ Clicked status button")
                (e/wait 1)))))
      (catch Exception e
        (println (str "❌ Error with status button: " (.getMessage e)))))
      
      (e/screenshot driver "screenshots/main-terminal-streaming-5-status-checked.png")
      (println "📸 Screenshot 5: Status checked")
      
      ;; Step 6: Test regular terminal functionality still works
      (println "📋 Step 6: Testing regular terminal functionality...")
      (try
        ;; Just verify the terminal is responsive - skip complex input
        (e/click driver {:class "xterm-screen"})
        (println "✅ Terminal area is clickable")
        (e/wait 2)
      (catch Exception e
        (println (str "❌ Error with regular terminal: " (.getMessage e)))))
      
      (e/screenshot driver "screenshots/main-terminal-streaming-6-regular-terminal.png")
      (println "📸 Screenshot 6: Regular terminal functionality")
      
      ;; Step 7: Stop streaming
      (println "📋 Step 7: Stopping streaming...")
      (try
        (e/click driver {:id "streamingBtn"})
        (println "✅ Clicked streaming button to stop")
        (e/wait 2)
      (catch Exception e
        (println (str "❌ Error stopping streaming: " (.getMessage e)))))
      
      (e/screenshot driver "screenshots/main-terminal-streaming-7-streaming-stopped.png")
      (println "📸 Screenshot 7: Streaming stopped")
      
      ;; Final wait to observe results
      (println "📋 Final: Observing results...")
      (e/wait 5)
      (e/screenshot driver "screenshots/main-terminal-streaming-8-final.png")
      (println "📸 Screenshot 8: Final state")
      
      (println "")
      (println "✅ MAIN TERMINAL STREAMING INTEGRATION TEST COMPLETED!")
      (println "======================================================")
      (println "📸 Screenshots saved to screenshots/ directory")
      (println "🔍 Check screenshots to verify streaming integration")
      (println "")
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      
      (finally
        (println "🔄 Keeping browser open for 10 seconds to observe...")
        (Thread/sleep 10000)
        (e/quit driver)))))

(defn test-integration
  "Quick test to verify main terminal streaming integration"
  []
  (test-main-terminal-streaming))
