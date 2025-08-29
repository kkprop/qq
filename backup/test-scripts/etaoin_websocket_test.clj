(ns qq.browser.etaoin-websocket-test
  "🎯 Most Convenient Etaoin Solution - Direct WebSocket Messaging
  
  This is the most reliable approach:
  1. Uses etaoin to open the browser
  2. Directly calls the WebSocket send function (same as our working manual test)
  3. No focus issues, no complex typing simulation
  4. Leverages the exact same mechanism that works manually
  
  This is the perfect balance of automation and reliability!"
  (:require [etaoin.api :as e]))

(defn send-question-via-websocket [driver question]
  "Send question directly via WebSocket using the same mechanism as manual test"
  (println (str "📤 Sending question via WebSocket: " question))
  
  ;; Use the exact same JavaScript code that works in our manual tests
  ;; Pass the question as a parameter to avoid string escaping issues
  (let [result (e/js-execute driver 
    "
    // This is the exact same code from our working manual test
    const question = arguments[0]; // Get question from parameter
    
    if (ws && ws.readyState === WebSocket.OPEN) {
      const message = {
        type: 'command',
        content: question,
        session: currentSessionId || 'default'
      };
      
      console.log('📤 Sending WebSocket message:', message);
      ws.send(JSON.stringify(message));
      
      // Also display in terminal for visual feedback (like manual typing)
      if (terminal) {
        terminal.write('\\r\\n> ' + question + '\\r\\n');
        terminal.write('⏳ Processing...\\r\\n');
      }
      
      console.log('✅ WebSocket message sent successfully');
      return 'success';
    } else {
      console.log('❌ WebSocket not available or not connected');
      return 'failed';
    }
    " question)] ; Pass question as argument
    
    (println (str "✅ WebSocket result: " result))))

(defn test-etaoin-websocket []
  "Test the most convenient etaoin approach - direct WebSocket messaging"
  (println "🎯 MOST CONVENIENT ETAOIN SOLUTION")
  (println "==================================")
  (println "")
  (println "📋 This approach:")
  (println "- ✅ Uses etaoin to open browser (reliable)")
  (println "- ✅ Directly sends WebSocket message (same as working manual test)")
  (println "- ✅ No focus issues or complex typing simulation")
  (println "- ✅ Leverages exact mechanism that works manually")
  (println "")
  
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
            
            ;; Send question directly via WebSocket
            (send-question-via-websocket driver question)
            
            ;; Wait for Q&A response
            (println "⏳ Waiting for Q&A response...")
            (Thread/sleep 12000) ; Wait longer for Q to process
            
            ;; Take screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/etaoin-websocket-test_" (.format timestamp now) ".png")]
              (println (str "📸 Taking screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "")
            (println "🎉 Most convenient etaoin test completed!")
            (println "🔍 Check browser and server logs for Q&A results"))
          
          (println "❌ WebSocket not connected - cannot test")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 5000) ; Keep browser open to see results
        (e/quit driver)))))
