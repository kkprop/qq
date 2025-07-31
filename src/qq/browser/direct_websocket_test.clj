(ns qq.browser.direct-websocket-test
  "🧪 Direct WebSocket Test - Test WebSocket communication directly"
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]))

(defn test-direct-websocket []
  "Test WebSocket communication directly by sending messages via JavaScript"
  (println "🧪 TESTING DIRECT WEBSOCKET COMMUNICATION")
  (println "=========================================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (println "🌐 Opening browser terminal interface...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      
      ;; Wait for page to load and WebSocket connection
      (Thread/sleep 5000)
      
      ;; Check connection status
      (let [status-text (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 Connection status: " status-text))
        
        (if (.contains status-text "Connected")
          (do
            (println "✅ WebSocket connection confirmed")
            
            ;; Send a direct WebSocket message via JavaScript
            (let [test-question "What is 2 + 2?"]
              (println (str "📤 Sending direct WebSocket message: " test-question))
              
              ;; Use JavaScript to send WebSocket message directly
              (e/js-execute driver 
                (str "
                  // Send message directly via WebSocket
                  const question = '" test-question "';
                  
                  if (window.ws && window.ws.readyState === WebSocket.OPEN) {
                    const message = {
                      type: 'command',
                      content: question,
                      session: 'qq-default'
                    };
                    
                    window.ws.send(JSON.stringify(message));
                    console.log('Direct WebSocket message sent:', message);
                    
                    // Also display in terminal for visual feedback
                    if (window.term) {
                      window.term.write('\\r\\n> ' + question + '\\r\\n');
                    }
                  } else {
                    console.log('WebSocket not available or not connected');
                  }
                "))
              
              (println "📤 Direct WebSocket message sent")
              
              ;; Wait for response
              (println "⏳ Waiting for server to process message...")
              (Thread/sleep 5000)
              
              ;; Take screenshot
              (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                    screenshot-path (str "screenshots/direct-websocket-test_" 
                                        (.format timestamp (java.time.LocalDateTime/now)) ".png")]
                (println (str "📸 Taking screenshot: " screenshot-path))
                (e/screenshot driver screenshot-path))
              
              (println "✅ Direct WebSocket test completed")
              (println "🔍 Check server logs and tmux session for results")))
          
          (do
            (println "❌ WebSocket not connected - cannot test")
            false)))
      
      (catch Exception e
        (println (str "❌ Direct WebSocket test failed: " (.getMessage e)))
        false)
      
      (finally
        (Thread/sleep 5000) ; Keep browser open to see results
        (e/quit driver)))))
