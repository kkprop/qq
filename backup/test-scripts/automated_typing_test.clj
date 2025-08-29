(ns qq.browser.automated-typing-test
  "🎯 Automated Typing Test - Multiple Approaches
  
  This test uses multiple methods to simulate real typing:
  1. etaoin's fill-human function
  2. etaoin's key action functions  
  3. sendkeys utility (if Chrome window is focused)
  4. AppleScript automation
  
  Tests the complete Enter key functionality!"
  (:require [etaoin.api :as e]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]))

(defn test-etaoin-fill-human [driver question]
  "Test using etaoin's fill-human function"
  (println "🔤 Testing etaoin fill-human approach...")
  (try
    ;; Click on terminal to focus
    (e/click driver {:css "#terminal"})
    (Thread/sleep 500)
    
    ;; Use fill-human to simulate human-like typing
    (e/fill-human driver {:css "#terminal"} question)
    (Thread/sleep 1000)
    
    ;; Send Enter key using key actions
    (e/perform-actions driver
      (-> (e/make-key-input)
          (e/add-key-press :enter)))
    (println "✅ etaoin fill-human completed")
    true
  (catch Exception e
    (println (str "❌ etaoin fill-human failed: " (.getMessage e)))
    false)))

(defn test-etaoin-key-actions [driver question]
  "Test using etaoin's key action functions"
  (println "🔤 Testing etaoin key actions approach...")
  (try
    ;; Click on terminal to focus
    (e/click driver {:css "#terminal"})
    (Thread/sleep 500)
    
    ;; Type each character using key actions
    (let [key-input (e/make-key-input)]
      (reduce (fn [input char]
                (-> input
                    (e/add-key-press (str char))
                    (e/add-pause 100))) ; 100ms delay between keys
              key-input
              (seq question))
      
      ;; Add Enter key
      (-> key-input
          (e/add-key-press :enter))
      
      ;; Perform all actions
      (e/perform-actions driver key-input))
    
    (println "✅ etaoin key actions completed")
    true
  (catch Exception e
    (println (str "❌ etaoin key actions failed: " (.getMessage e)))
    false)))

(defn test-sendkeys-utility [question]
  "Test using local sendkeys utility"
  (println "🔤 Testing sendkeys utility approach...")
  (try
    ;; First, try to focus Chrome window
    (shell/sh "osascript" "-e" "tell application \"Google Chrome\" to activate")
    (Thread/sleep 1000)
    
    ;; Use sendkeys to type the question
    (let [result (shell/sh "sendkeys" "send" question)]
      (if (= 0 (:exit result))
        (do
          (Thread/sleep 500)
          ;; Send Enter key
          (shell/sh "sendkeys" "send" "\\r")
          (println "✅ sendkeys utility completed")
          true)
        (do
          (println (str "❌ sendkeys failed: " (:err result)))
          false)))
  (catch Exception e
    (println (str "❌ sendkeys utility failed: " (.getMessage e)))
    false)))

(defn test-applescript-typing [question]
  "Test using AppleScript for typing"
  (println "🔤 Testing AppleScript approach...")
  (try
    ;; Use AppleScript to type in the active window
    (let [script (str "tell application \"System Events\" to keystroke \"" question "\"")
          result (shell/sh "osascript" "-e" script)]
      (if (= 0 (:exit result))
        (do
          (Thread/sleep 500)
          ;; Send Enter key
          (shell/sh "osascript" "-e" "tell application \"System Events\" to key code 36") ; Enter key
          (println "✅ AppleScript completed")
          true)
        (do
          (println (str "❌ AppleScript failed: " (:err result)))
          false)))
  (catch Exception e
    (println (str "❌ AppleScript failed: " (.getMessage e)))
    false)))

(defn test-automated-typing []
  "Test automated typing using multiple approaches"
  (println "🎯 COMPREHENSIVE AUTOMATED TYPING TEST")
  (println "=====================================")
  
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
            (println "🎯 Testing multiple typing approaches...")
            (println "")
            
            ;; Test 1: etaoin fill-human
            (println "📝 TEST 1: etaoin fill-human")
            (test-etaoin-fill-human driver question)
            (Thread/sleep 3000)
            
            ;; Clear terminal for next test
            (e/perform-actions driver
              (-> (e/make-key-input)
                  (e/add-key-down :control)
                  (e/add-key-press "l")
                  (e/add-key-up :control)))
            (Thread/sleep 1000)
            
            ;; Test 2: etaoin key actions
            (println "")
            (println "📝 TEST 2: etaoin key actions")
            (test-etaoin-key-actions driver question)
            (Thread/sleep 3000)
            
            ;; Test 3: sendkeys utility (requires Chrome to be focused)
            (println "")
            (println "📝 TEST 3: sendkeys utility")
            (println "⚠️  This requires Chrome window to be focused...")
            (test-sendkeys-utility question)
            (Thread/sleep 3000)
            
            ;; Test 4: AppleScript
            (println "")
            (println "📝 TEST 4: AppleScript")
            (println "⚠️  This requires Chrome window to be active...")
            (test-applescript-typing question)
            (Thread/sleep 3000)
            
            ;; Take final screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/automated-typing-test_" (.format timestamp now) ".png")]
              (println (str "📸 Taking final screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "")
            (println "✅ Automated typing test completed")
            (println "🔍 Check browser behavior and server logs for results"))
          
          (println "❌ WebSocket not connected - cannot test automated typing")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 5000) ; Keep browser open to see results
        (e/quit driver)))))
