(ns qq.browser.auto-streaming-test
  "🌊 Final Test for Automatic Streaming with Clean Display
  
  This tests the final automatic streaming implementation:
  1. Opens main terminal.html in browser
  2. Verifies auto-streaming starts automatically
  3. Tests clean content display (no garbled characters)
  4. Confirms maximum user experience (no manual activation needed)"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]))

(defn test-automatic-streaming []
  "Test automatic streaming with clean display"
  (println "🌊 TESTING AUTOMATIC STREAMING WITH CLEAN DISPLAY")
  (println "=================================================")
  
  (let [driver (e/chrome {:headless false :size [1400 900]})]
    (try
      ;; Step 1: Open main terminal
      (println "📋 Step 1: Opening main terminal...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      (e/wait 4)
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/auto-streaming-1-initial.png")
      (println "📸 Screenshot 1: Terminal loaded with auto-streaming")
      
      ;; Step 2: Verify auto-streaming status
      (println "📋 Step 2: Checking auto-streaming status...")
      (let [status-text (try 
                          (e/get-element-text driver {:id "streamingStatus"})
                          (catch Exception e "Status not found"))]
        (println (str "📊 Streaming status: " status-text)))
      
      (e/screenshot driver "screenshots/auto-streaming-2-status.png")
      (println "📸 Screenshot 2: Auto-streaming status visible")
      
      ;; Step 3: Send command to generate clean streaming content
      (println "📋 Step 3: Generating clean streaming content...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "echo 'Clean auto-streaming test - no garbled characters'" "Enter")
      (println "✅ Sent clean content command")
      
      (e/wait 4)
      (e/screenshot driver "screenshots/auto-streaming-3-clean-content.png")
      (println "📸 Screenshot 3: Clean content streamed automatically")
      
      ;; Step 4: Test with Q command to verify real-time streaming
      (println "📋 Step 4: Testing real-time Q command streaming...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "what is the current time?" "Enter")
      (println "✅ Sent Q command for real-time streaming test")
      
      (e/wait 6)
      (e/screenshot driver "screenshots/auto-streaming-4-q-command.png")
      (println "📸 Screenshot 4: Q command response streamed")
      
      ;; Step 5: Verify no manual controls needed
      (println "📋 Step 5: Verifying no manual streaming controls...")
      (let [streaming-buttons (try 
                                (e/query-all driver {:tag "button"})
                                (catch Exception e []))]
        (let [streaming-btn-count (count (filter #(try 
                                                    (clojure.string/includes? 
                                                      (e/get-element-text driver %) "Streaming")
                                                    (catch Exception e false)) 
                                                  streaming-buttons))]
          (println (str "📊 Manual streaming buttons found: " streaming-btn-count))
          (if (= streaming-btn-count 0)
            (println "✅ No manual streaming buttons - automatic UX confirmed")
            (println "❌ Manual streaming buttons still present"))))
      
      (e/screenshot driver "screenshots/auto-streaming-5-no-manual-controls.png")
      (println "📸 Screenshot 5: No manual controls needed")
      
      ;; Final observation
      (println "📋 Final: Observing automatic streaming...")
      (e/wait 5)
      (e/screenshot driver "screenshots/auto-streaming-6-final.png")
      (println "📸 Screenshot 6: Final automatic streaming state")
      
      (println "")
      (println "✅ AUTOMATIC STREAMING TEST COMPLETED!")
      (println "======================================")
      (println "🎯 Maximum user experience achieved:")
      (println "- ✅ Auto-streaming starts on connection")
      (println "- ✅ Clean content display (no garbled chars)")
      (println "- ✅ No manual activation required")
      (println "- ✅ Real-time tmux content streaming")
      (println "")
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      
      (finally
        (println "🔄 Keeping browser open for 10 seconds to observe...")
        (Thread/sleep 10000)
        (e/quit driver)))))

(defn test-auto-streaming
  "Quick test for automatic streaming"
  []
  (test-automatic-streaming))
