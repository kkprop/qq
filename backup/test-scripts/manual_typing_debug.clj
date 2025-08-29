(ns qq.browser.manual-typing-debug
  "🔍 Manual Typing Debug - Investigate Enter Key Issue
  
  This test helps debug why manual typing Enter key doesn't work
  while automated WebSocket sending does work."
  (:require [etaoin.api :as e]))

(defn add-debug-logging [driver]
  "Add debug logging to the terminal to see what's happening"
  (println "🔍 Adding debug logging to terminal...")
  
  (e/js-execute driver "
    // Add debug logging to see what's happening with manual typing
    console.log('🔍 Adding debug logging...');
    
    // Log WebSocket events
    if (ws) {
      const originalOnMessage = ws.onmessage;
      ws.onmessage = function(event) {
        console.log('📨 WebSocket message received:', event.data);
        if (originalOnMessage) {
          originalOnMessage.call(this, event);
        }
      };
      console.log('✅ WebSocket message logging added');
    }
    
    // Log terminal onData events
    if (terminal && terminal.onData) {
      const originalOnData = terminal.onData;
      terminal.onData = function(data) {
        console.log('⌨️ Terminal onData received:', JSON.stringify(data));
        if (originalOnData) {
          originalOnData.call(this, data);
        }
      };
      console.log('✅ Terminal onData logging added');
    }
    
    console.log('🔍 Debug logging setup complete');
  ")
  
  (println "✅ Debug logging added"))

(defn test-manual-typing-debug []
  "Test manual typing with debug logging"
  (println "🔍 MANUAL TYPING DEBUG TEST")
  (println "===========================")
  (println "")
  (println "📋 This test will:")
  (println "- Open browser terminal with debug logging")
  (println "- Wait for you to type manually")
  (println "- Show debug information in browser console")
  (println "")
  
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
            (println "")
            
            ;; Add debug logging
            (add-debug-logging driver)
            (println "")
            
            (println "🎯 MANUAL TESTING INSTRUCTIONS:")
            (println "==============================")
            (println "")
            (println "1. 📝 Click in the terminal area")
            (println "2. ⌨️ Type: test message")
            (println "3. ⏎ Press Enter")
            (println "4. 👀 Watch what happens")
            (println "5. 🔍 Open browser console (F12) to see debug logs")
            (println "")
            (println "📋 Expected behavior:")
            (println "- ✅ Characters should appear as you type")
            (println "- ✅ Enter should send command and show new prompt")
            (println "- ✅ Debug logs should show onData and WebSocket events")
            (println "")
            (println "⏰ Browser will stay open for 60 seconds for manual testing...")
            
            ;; Keep browser open for manual testing
            (Thread/sleep 60000)
            
            ;; Take final screenshot
            (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
                  now (java.time.LocalDateTime/now)
                  filename (str "screenshots/manual-typing-debug_" (.format timestamp now) ".png")]
              (println (str "📸 Taking final screenshot: " filename))
              (e/screenshot driver filename))
            
            (println "")
            (println "✅ Manual typing debug test completed")
            (println "🔍 Check browser console logs for debug information"))
          
          (println "❌ WebSocket not connected - cannot test manual typing")))
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (e/quit driver)))))
