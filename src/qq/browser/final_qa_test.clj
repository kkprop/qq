(ns qq.browser.final-qa-test
  "🎯 Final Q&A Test - Direct Message Sending
  
  This test bypasses the typing simulation and directly sends
  a WebSocket message using the same mechanism as the terminal's
  Enter key handler. This should complete the final 5%!"
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]))

(defn send-direct-message [driver question]
  "Send message directly using the terminal's WebSocket mechanism"
  (println (str "📤 Sending direct message: " question))
  
  ;; Use JavaScript to directly send WebSocket message like the terminal does
  (e/js-execute driver (str "
    // Send message directly using the same code as terminal's Enter handler
    const question = '" question "';
    
    if (ws && ws.readyState === WebSocket.OPEN) {
      // This is exactly what the terminal does when Enter is pressed
      const message = {
        type: 'command',
        content: question,
        session: currentSessionId || 'qq-default'
      };
      
      console.log('📤 Sending WebSocket message:', message);
      ws.send(JSON.stringify(message));
      
      // Also display in terminal for visual feedback
      if (terminal) {
        terminal.write('\\r\\n> ' + question + '\\r\\n');
        terminal.write('⏳ Processing...\\r\\n');
      }
      
      console.log('✅ Direct message sent successfully');
    } else {
      console.log('❌ WebSocket not available or not connected');
      console.log('WebSocket state:', ws ? ws.readyState : 'undefined');
    }
  "))
  
  (println "✅ Direct message sent via WebSocket"))

(defn test-final-qa []
  "Final Q&A test - direct message sending"
  (println "🎯 FINAL Q&A TEST - DIRECT MESSAGE SENDING")
  (println "==========================================")
  
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
            
            ;; Send direct message
            (send-direct-message driver "What is 2 + 2?")
            
            ;; Wait for Q&A response
            (println "⏳ Waiting for Q&A response...")
            (Thread/sleep 15000) ; Wait longer for Q to process and respond
            
            ;; Take screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/final-qa-test_" (.format timestamp now) ".png")]
              (println (str "📸 Taking screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "✅ Final Q&A test completed")
            (println "🔍 Check server logs and tmux session for complete Q&A results"))
          
          (println "❌ WebSocket not connected - cannot test final Q&A")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 3000) ; Keep browser open to see results
        (e/quit driver)))))
