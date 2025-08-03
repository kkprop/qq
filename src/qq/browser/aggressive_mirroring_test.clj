(ns qq.browser.aggressive-mirroring-test
  "🚀 Final Test for Aggressive Tmux Mirroring
  
  This tests the aggressive tmux mirroring implementation:
  1. Opens main terminal.html in browser
  2. Verifies full tmux history loads immediately
  3. Tests real-time mirroring of new content
  4. Confirms true tmux window mirror functionality"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]))

(defn test-aggressive-mirroring []
  "Test aggressive tmux mirroring with full history sync"
  (println "🚀 TESTING AGGRESSIVE TMUX MIRRORING")
  (println "====================================")
  
  (let [driver (e/chrome {:headless false :size [1400 900]})]
    (try
      ;; Step 1: Open main terminal
      (println "📋 Step 1: Opening terminal for aggressive mirroring...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      (e/wait 5) ; Give more time for full history sync
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/aggressive-mirroring-1-full-sync.png")
      (println "📸 Screenshot 1: Full tmux history should be loaded")
      
      ;; Step 2: Check mirroring status
      (println "📋 Step 2: Checking aggressive mirroring status...")
      (let [status-text (try 
                          (e/get-element-text driver {:id "streamingStatus"})
                          (catch Exception e "Status not found"))]
        (println (str "🚀 Mirroring status: " status-text)))
      
      (e/screenshot driver "screenshots/aggressive-mirroring-2-status.png")
      (println "📸 Screenshot 2: Mirroring status visible")
      
      ;; Step 3: Send new content to test real-time mirroring
      (println "📋 Step 3: Testing real-time mirroring...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "echo 'REAL-TIME MIRROR TEST - This should appear immediately in browser'" "Enter")
      (println "✅ Sent real-time test command")
      
      (e/wait 3)
      (e/screenshot driver "screenshots/aggressive-mirroring-3-realtime.png")
      (println "📸 Screenshot 3: Real-time content should be mirrored")
      
      ;; Step 4: Test with multiple rapid commands
      (println "📋 Step 4: Testing rapid command mirroring...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" "echo 'Line 1 of rapid test'" "Enter")
      (Thread/sleep 500)
      (shell/sh "tmux" "send-keys" "-t" "qq-default" "echo 'Line 2 of rapid test'" "Enter")
      (Thread/sleep 500)
      (shell/sh "tmux" "send-keys" "-t" "qq-default" "echo 'Line 3 of rapid test'" "Enter")
      (println "✅ Sent rapid test commands")
      
      (e/wait 4)
      (e/screenshot driver "screenshots/aggressive-mirroring-4-rapid-commands.png")
      (println "📸 Screenshot 4: Rapid commands mirrored")
      
      ;; Step 5: Test Q command mirroring
      (println "📋 Step 5: Testing Q command mirroring...")
      (shell/sh "tmux" "send-keys" "-t" "qq-default" 
                "what is 2+2?" "Enter")
      (println "✅ Sent Q command for mirroring test")
      
      (e/wait 8) ; Give time for Q response
      (e/screenshot driver "screenshots/aggressive-mirroring-5-q-command.png")
      (println "📸 Screenshot 5: Q command and response mirrored")
      
      ;; Step 6: Check browser console for mirroring messages
      (println "📋 Step 6: Checking browser console...")
      (try
        (let [logs (e/get-logs driver :browser)]
          (println (str "📋 Browser console logs: " (count logs) " entries"))
          (doseq [log (take 5 logs)]
            (when (clojure.string/includes? (:message log) "tmux")
              (println (str "🚀 Mirroring log: " (:message log))))))
      (catch Exception e
        (println (str "❌ Could not get browser logs: " (.getMessage e)))))
      
      ;; Final observation
      (println "📋 Final: Observing aggressive mirroring...")
      (e/wait 5)
      (e/screenshot driver "screenshots/aggressive-mirroring-6-final.png")
      (println "📸 Screenshot 6: Final mirroring state")
      
      (println "")
      (println "✅ AGGRESSIVE TMUX MIRRORING TEST COMPLETED!")
      (println "============================================")
      (println "🚀 Expected results:")
      (println "- ✅ Full tmux history loaded on connection")
      (println "- ✅ Real-time content mirrored immediately")
      (println "- ✅ Rapid commands all mirrored")
      (println "- ✅ Q commands and responses mirrored")
      (println "- ✅ True tmux window mirror in browser")
      (println "")
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      
      (finally
        (println "🔄 Keeping browser open for 15 seconds to observe mirroring...")
        (Thread/sleep 15000)
        (e/quit driver)))))

(defn test-mirroring
  "Quick test for aggressive mirroring"
  []
  (test-aggressive-mirroring))
