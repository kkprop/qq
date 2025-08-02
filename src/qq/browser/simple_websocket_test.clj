(ns qq.browser.simple-websocket-test
  "🧪 Simple WebSocket Test - Direct WebSocket Testing
  
  This test verifies our WebSocket server functionality without
  depending on the web server. It creates a minimal HTML page
  and tests the WebSocket connection directly."
  (:require [etaoin.api :as e]))

(defn create-test-html []
  "Create a minimal HTML page for WebSocket testing"
  (str "<!DOCTYPE html>
<html>
<head>
    <title>WebSocket Test</title>
</head>
<body>
    <div id=\"status\">Initializing...</div>
    <div id=\"statusText\">Disconnected</div>
    <div id=\"output\"></div>
    
    <script>
        let ws;
        let currentSessionId = 'default';
        
        function connectWebSocket() {
            const wsUrl = 'ws://localhost:9091/terminal/' + currentSessionId;
            console.log('Connecting to:', wsUrl);
            
            ws = new WebSocket(wsUrl);
            
            ws.onopen = function(event) {
                console.log('WebSocket connected');
                document.getElementById('statusText').textContent = 'Connected';
                document.getElementById('status').textContent = 'Connected to WebSocket';
            };
            
            ws.onmessage = function(event) {
                console.log('WebSocket message:', event.data);
                const data = JSON.parse(event.data);
                const output = document.getElementById('output');
                output.innerHTML += '<div>' + data.content + '</div>';
            };
            
            ws.onerror = function(error) {
                console.error('WebSocket error:', error);
                document.getElementById('statusText').textContent = 'Error';
            };
            
            ws.onclose = function(event) {
                console.log('WebSocket closed');
                document.getElementById('statusText').textContent = 'Disconnected';
            };
        }
        
        // Connect when page loads
        window.onload = connectWebSocket;
        
        // Test function to send a message
        function sendTestMessage() {
            if (ws && ws.readyState === WebSocket.OPEN) {
                const message = {
                    type: 'command',
                    content: 'what\\'s the time?',
                    session: currentSessionId
                };
                ws.send(JSON.stringify(message));
                return 'success';
            }
            return 'failed';
        }
        
        // Make function available globally
        window.sendTestMessage = sendTestMessage;
    </script>
</body>
</html>"))

(defn test-websocket-directly []
  "Test WebSocket functionality directly without web server"
  (println "🧪 SIMPLE WEBSOCKET TEST")
  (println "=========================")
  (println "")
  (println "📋 This test:")
  (println "- Creates a minimal HTML page with WebSocket client")
  (println "- Tests WebSocket connection to port 9091")
  (println "- Sends a test message and verifies response")
  (println "- Doesn't depend on external web server")
  (println "")
  
  (let [driver (e/chrome {:headless false})
        test-html (create-test-html)]
    (try
      ;; Load the test HTML directly
      (println "🌐 Loading test HTML page...")
      (e/go driver (str "data:text/html;charset=utf-8," (java.net.URLEncoder/encode test-html "UTF-8")))
      
      ;; Wait for page to load completely
      (println "⏳ Waiting for page to load...")
      (Thread/sleep 2000)
      
      ;; Wait for elements to be available
      (e/wait-visible driver {:css "#statusText"} {:timeout 10})
      (Thread/sleep 3000) ; Additional wait for WebSocket connection
      
      ;; Check connection status
      (let [status (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 WebSocket status: " status))
        
        (if (= status "Connected")
          (do
            (println "✅ WebSocket connection successful!")
            (println "")
            
            ;; Send test message
            (println "📤 Sending test message...")
            (let [result (e/js-execute driver "return window.sendTestMessage();")]
              (println (str "📤 Send result: " result))
              
              (if (= result "success")
                (do
                  (println "✅ Test message sent successfully")
                  
                  ;; Wait for response
                  (println "⏳ Waiting for response...")
                  (Thread/sleep 8000)
                  
                  ;; Check for response
                  (let [output (e/get-element-text driver {:css "#output"})]
                    (if (and output (not (empty? output)))
                      (do
                        (println "✅ Response received!")
                        (println (str "📨 Response: " (take 100 output) "..."))
                        (println "")
                        (println "🎉 WebSocket test SUCCESSFUL!"))
                      (println "❌ No response received"))))
                
                (println "❌ Failed to send test message"))))
          
          (println "❌ WebSocket connection failed")))
      
      ;; Take screenshot
      (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
            now (java.time.LocalDateTime/now)
            filename (str "screenshots/simple-websocket-test_" (.format timestamp now) ".png")]
        (println (str "📸 Taking screenshot: " filename))
        (e/screenshot driver filename))
      
      (println "")
      (println "✅ Simple WebSocket test completed!")
      
      (catch Exception e
        (println (str "❌ Test error: " (.getMessage e))))
      (finally
        (Thread/sleep 3000) ; Keep browser open to see results
        (e/quit driver)))))
