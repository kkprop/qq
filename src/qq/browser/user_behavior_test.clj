(ns qq.browser.user-behavior-test
  "🎯 User Behavior Simulation Test - Actual Terminal Typing
  
  This test simulates real user behavior:
  1. Opens browser terminal
  2. Types a question character by character
  3. Presses Enter to send the message
  4. Waits for Q&A response
  
  This is how users actually interact with the terminal!"
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]))

(defn simulate-typing [driver question]
  "Simulate actual user typing in the terminal using JavaScript"
  (println (str "⌨️ Simulating user typing: " question))
  
  ;; Use JavaScript to simulate typing in the xterm.js terminal
  (e/js-execute driver (str "
    // Simulate typing each character
    const question = '" question "';
    let index = 0;
    
    function typeNextChar() {
      if (index < question.length) {
        const char = question[index];
        
        // The terminal is created as 'const terminal' in the script
        // We need to access it directly, not via window.terminal
        if (typeof terminal !== 'undefined' && terminal.onData) {
          terminal.onData(char);
          console.log('📝 Typed character: ' + char);
        } else {
          console.log('❌ Terminal not found or onData not available');
        }
        
        index++;
        setTimeout(typeNextChar, 100); // 100ms delay between characters
      } else {
        // After typing all characters, send Enter (\\r)
        setTimeout(() => {
          if (typeof terminal !== 'undefined' && terminal.onData) {
            terminal.onData('\\r'); // Send Enter key
            console.log('✅ Simulated Enter key press');
          }
        }, 200);
      }
    }
    
    // Start typing simulation
    typeNextChar();
    console.log('🎯 Started typing simulation for: ' + question);
  "))
  
  (println "✅ User typing simulation initiated via JavaScript"))

(defn test-user-behavior []
  "Test complete user behavior simulation"
  (println "🎯 TESTING REAL USER BEHAVIOR SIMULATION")
  (println "========================================")
  
  (let [driver (e/chrome {:headless false})] ; Keep browser visible
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
            
            ;; Simulate real user typing
            (simulate-typing driver "What is 2 + 2?")
            
            ;; Wait for response
            (println "⏳ Waiting for Q&A response...")
            (Thread/sleep 10000) ; Wait longer for Q to respond
            
            ;; Take screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/user-behavior-test_" (.format timestamp now) ".png")]
              (println (str "📸 Taking screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "✅ User behavior test completed")
            (println "🔍 Check server logs and tmux session for Q&A results"))
          
          (println "❌ WebSocket not connected - cannot test user behavior")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 2000) ; Keep browser open briefly to see results
        (e/quit driver)))))
