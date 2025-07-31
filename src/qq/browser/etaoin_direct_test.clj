(ns qq.browser.etaoin-direct-test
  "🎯 Etaoin Direct Test - JavaScript Event Simulation
  
  This test uses etaoin's JavaScript execution to directly trigger
  the terminal's onData events, simulating real user typing without
  needing to focus windows or send system keystrokes.
  
  This is much more reliable and convenient!"
  (:require [etaoin.api :as e]))

(defn simulate-real-typing [driver text]
  "Simulate real typing by triggering terminal onData events"
  (println (str "⌨️ Simulating real typing: " text))
  
  ;; Use JavaScript to simulate each keystroke through the terminal's onData handler
  (e/js-execute driver (str "
    // Function to simulate typing character by character
    function simulateTyping(text) {
      let index = 0;
      
      function typeNextChar() {
        if (index < text.length) {
          const char = text[index];
          
          // Trigger the terminal's onData handler for each character
          if (typeof terminal !== 'undefined' && terminal.onData) {
            // Create a proper keyboard event
            terminal.onData(char);
            console.log('Typed character:', char);
          }
          
          index++;
          // Small delay between characters for realistic typing
          setTimeout(typeNextChar, 50);
        } else {
          console.log('Typing completed');
        }
      }
      
      typeNextChar();
    }
    
    // Start typing simulation with properly escaped string
    simulateTyping(" (pr-str text) ");
  "))
  
  ;; Wait for typing to complete
  (Thread/sleep (+ 1000 (* (count text) 60))) ; Base time + char time
  (println "✅ Typing simulation completed"))

(defn simulate-enter-key [driver]
  "Simulate pressing Enter key"
  (println "⏎ Simulating Enter key press...")
  
  (e/js-execute driver "
    // Simulate Enter key press
    if (typeof terminal !== 'undefined' && terminal.onData) {
      terminal.onData('\\r'); // Send carriage return (Enter)
      console.log('Enter key pressed');
    }
  ")
  
  (Thread/sleep 500)
  (println "✅ Enter key simulation completed"))

(defn test-etaoin-direct-typing []
  "Test direct typing using etaoin JavaScript execution"
  (println "🎯 ETAOIN DIRECT TYPING TEST")
  (println "============================")
  
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
            
            ;; Simulate real typing
            (simulate-real-typing driver question)
            
            ;; Simulate Enter key
            (simulate-enter-key driver)
            
            ;; Wait for Q&A response
            (println "⏳ Waiting for Q&A response...")
            (Thread/sleep 10000)
            
            ;; Take screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/etaoin-direct-test_" (.format timestamp now) ".png")]
              (println (str "📸 Taking screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "")
            (println "🎉 Etaoin direct typing test completed!")
            (println "🔍 Check browser and server logs for Q&A results"))
          
          (println "❌ WebSocket not connected - cannot test direct typing")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 5000) ; Keep browser open to see results
        (e/quit driver)))))
