(ns qq.browser.improved-sendkeys-test
  "🎯 Improved SendKeys Test - Better Focus Handling
  
  This test is more resilient to focus issues:
  1. Warns user to not use computer during test
  2. Uses multiple focus attempts
  3. Shorter delays to minimize focus loss
  4. Clear success/failure indicators"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]))

(defn force-chrome-focus []
  "Aggressively focus Chrome window"
  (println "🔍 Forcing Chrome window focus...")
  (try
    ;; Multiple attempts to focus Chrome
    (shell/sh "osascript" "-e" "tell application \"Google Chrome\" to activate")
    (Thread/sleep 500)
    (shell/sh "osascript" "-e" "tell application \"System Events\" to tell process \"Google Chrome\" to set frontmost to true")
    (Thread/sleep 500)
    (println "✅ Chrome focus attempts completed")
    true
  (catch Exception e
    (println (str "❌ Error focusing Chrome: " (.getMessage e)))
    false)))

(defn test-improved-sendkeys []
  "Improved sendkeys test with better focus handling"
  (println "🎯 IMPROVED SENDKEYS TEST")
  (println "=========================")
  (println "")
  (println "⚠️  IMPORTANT: Please don't use the computer during this test!")
  (println "⚠️  The test needs to maintain focus on the Chrome window.")
  (println "")
  (println "Press Enter to continue or Ctrl+C to cancel...")
  (read-line)
  
  (let [driver (e/chrome {:headless false})
        question "what's the time?"]
    (try
      ;; Open browser terminal
      (println "🌐 Opening browser terminal...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      (Thread/sleep 3000)
      
      ;; Check connection
      (let [status (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 Connection: " status))
        
        (if (= status "Connected")
          (do
            ;; Click terminal and force focus
            (println "🎯 Focusing terminal...")
            (e/click driver {:css "#terminal"})
            (Thread/sleep 500)
            (force-chrome-focus)
            
            (println "")
            (println "⌨️ Sending keystrokes in 3 seconds...")
            (println "3...") (Thread/sleep 1000)
            (println "2...") (Thread/sleep 1000) 
            (println "1...") (Thread/sleep 1000)
            (println "GO!")
            
            ;; Send keystrokes quickly
            (let [keystroke-result (shell/sh "sendkeys" "send" question)]
              (if (= 0 (:exit keystroke-result))
                (do
                  (println "✅ Question sent")
                  (Thread/sleep 200) ; Minimal delay
                  
                  ;; Send Enter immediately
                  (let [enter-result (shell/sh "sendkeys" "send" "\\r")]
                    (if (= 0 (:exit enter-result))
                      (do
                        (println "✅ Enter sent")
                        (println "⏳ Waiting for response...")
                        (Thread/sleep 8000)
                        
                        ;; Take screenshot
                        (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                              now (java.time.LocalDateTime/now)
                              filename (str "screenshots/improved-sendkeys_" (.format timestamp now) ".png")]
                          (e/screenshot driver filename)
                          (println (str "📸 Screenshot: " filename)))
                        
                        (println "🎉 Test completed!"))
                      (println "❌ Failed to send Enter"))))
                (println "❌ Failed to send keystrokes"))))
          (println "❌ Not connected")))
      
      (catch Exception e
        (println (str "❌ Error: " (.getMessage e))))
      (finally
        (Thread/sleep 3000)
        (e/quit driver)))))
