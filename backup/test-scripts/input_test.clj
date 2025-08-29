(ns qq.browser.input-test
  "🧪 Browser Input Testing - Simulate typing in terminal interface"
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]))

(defn test-browser-input []
  "Test typing input in browser terminal and verify it reaches tmux"
  (println "🧪 TESTING BROWSER INPUT SIMULATION")
  (println "===================================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (println "🌐 Opening browser terminal interface...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      
      ;; Wait for page to load
      (Thread/sleep 3000)
      
      ;; Take screenshot of initial state
      (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
            screenshot-path (str "screenshots/input-test-initial_" 
                                (.format timestamp (java.time.LocalDateTime/now)) ".png")]
        (println (str "📸 Taking initial screenshot: " screenshot-path))
        (e/screenshot driver screenshot-path)
        (println (str "📸 Screenshot saved: " screenshot-path)))
      
      ;; Wait for WebSocket connection
      (println "⏳ Waiting for WebSocket connection...")
      (Thread/sleep 5000)
      
      ;; Check connection status
      (let [status-text (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 Connection status: " status-text)))
      
      ;; Find the terminal canvas and click it to focus
      (println "🎯 Clicking on terminal to focus...")
      (e/click driver {:css ".xterm-screen"})
      
      ;; Type a test command using JavaScript
      (let [test-command "echo Hello from browser terminal"
            timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")]
        (println (str "⌨️ Typing test command: " test-command))
        
        ;; Use JavaScript to simulate typing
        (e/js-execute driver 
          (str "
            // Simulate typing each character
            const command = '" test-command "';
            const terminal = window.term; // Assuming xterm instance is available
            
            if (terminal) {
              // Send each character to the terminal
              for (let i = 0; i < command.length; i++) {
                terminal.onData(command[i]);
              }
              // Send Enter key
              terminal.onData('\\r');
            } else {
              console.log('Terminal not found');
            }
          "))
        
        (println "↩️ Command sent via JavaScript...")
        
        ;; Wait for response
        (Thread/sleep 2000)
        
        ;; Take screenshot after input
        (let [screenshot-path (str "screenshots/input-test-after_" 
                                  (.format timestamp (java.time.LocalDateTime/now)) ".png")]
          (println (str "📸 Taking after-input screenshot: " screenshot-path))
          (e/screenshot driver screenshot-path)
          (println (str "📸 Screenshot saved: " screenshot-path)))
        
        ;; Get terminal content from xterm
        (let [terminal-content (try 
                                (e/get-element-text driver {:css ".xterm-rows"})
                                (catch Exception e "Could not read terminal content"))]
          (println (str "📋 Terminal content after input: " terminal-content)))
        
        (println (str "✅ Test command sent: " test-command))
        (println "🎯 Check your qq-default tmux session to see if the command appeared!"))
      
      (println "🧪 Browser input test completed")
      
      (catch Exception e
        (println (str "❌ Browser input test failed: " (.getMessage e))))
      
      (finally
        (e/quit driver)))))
