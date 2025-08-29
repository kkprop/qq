(ns qq.browser.sendkeys-test
  "🎯 SendKeys Test - Using Local SendKeys Utility
  
  This test uses the local sendkeys utility to simulate real typing
  by finding the Chrome window and sending keystrokes directly to it.
  This should work exactly like manual typing!"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]))

(defn focus-chrome-window []
  "Focus the Chrome window using AppleScript"
  (println "🔍 Focusing Chrome window...")
  (try
    (let [result (shell/sh "osascript" "-e" "tell application \"Google Chrome\" to activate")]
      (if (= 0 (:exit result))
        (do
          (Thread/sleep 1000) ; Wait for window to focus
          (println "✅ Chrome window focused")
          true)
        (do
          (println (str "❌ Failed to focus Chrome: " (:err result)))
          false)))
    (catch Exception e
      (println (str "❌ Error focusing Chrome: " (.getMessage e)))
      false)))

(defn send-keystrokes [text]
  "Send keystrokes using the sendkeys utility"
  (println (str "⌨️ Sending keystrokes: " text))
  (try
    (let [result (shell/sh "sendkeys" "send" text)]
      (if (= 0 (:exit result))
        (do
          (println "✅ Keystrokes sent successfully")
          true)
        (do
          (println (str "❌ Failed to send keystrokes: " (:err result)))
          false)))
    (catch Exception e
      (println (str "❌ Error sending keystrokes: " (.getMessage e)))
      false)))

(defn send-enter-key []
  "Send Enter key using sendkeys"
  (println "⏎ Sending Enter key...")
  (try
    (let [result (shell/sh "sendkeys" "send" "\\r")]
      (if (= 0 (:exit result))
        (do
          (println "✅ Enter key sent successfully")
          true)
        (do
          (println (str "❌ Failed to send Enter key: " (:err result)))
          false)))
    (catch Exception e
      (println (str "❌ Error sending Enter key: " (.getMessage e)))
      false)))

(defn test-sendkeys-typing []
  "Test typing using sendkeys utility"
  (println "🎯 SENDKEYS TYPING TEST")
  (println "======================")
  
  (let [driver (e/chrome {:headless false}) ; Keep browser visible
        question "what's the time?"]
    (try
      ;; Open browser terminal
      (println "🌐 Opening browser terminal interface...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      (Thread/sleep 5000) ; Wait for WebSocket connection
      
      ;; Check connection status
      (let [status (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 Connection status: " status))
        
        (if (= status "Connected")
          (do
            (println "✅ WebSocket connection confirmed")
            (println "")
            
            ;; Click on terminal to focus it
            (println "🎯 Clicking on terminal to focus...")
            (e/click driver {:css "#terminal"})
            (Thread/sleep 1000)
            
            ;; Focus Chrome window
            (if (focus-chrome-window)
              (do
                ;; Send the question using sendkeys
                (if (send-keystrokes question)
                  (do
                    (Thread/sleep 500)
                    ;; Send Enter key
                    (if (send-enter-key)
                      (do
                        (println "")
                        (println "✅ Complete typing sequence sent!")
                        (println "⏳ Waiting for Q&A response...")
                        (Thread/sleep 10000) ; Wait for response
                        
                        ;; Take screenshot
                        (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                              now (java.time.LocalDateTime/now)
                              filename (str "screenshots/sendkeys-test_" (.format timestamp now) ".png")]
                          (println (str "📸 Taking screenshot: " filename))
                          (e/screenshot driver filename))
                        
                        (println "")
                        (println "🎉 SendKeys test completed successfully!")
                        (println "🔍 Check browser and server logs for Q&A results"))
                      (println "❌ Failed to send Enter key")))
                  (println "❌ Failed to send keystrokes")))
              (println "❌ Failed to focus Chrome window")))
          
          (println "❌ WebSocket not connected - cannot test sendkeys typing")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 5000) ; Keep browser open to see results
        (e/quit driver)))))
