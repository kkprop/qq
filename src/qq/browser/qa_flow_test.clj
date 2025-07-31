(ns qq.browser.qa-flow-test
  "🧪 Complete Q&A Flow Test - Ask questions and verify answers through browser"
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

(defn wait-for-response [driver timeout-seconds]
  "Wait for response to appear in terminal"
  (let [start-time (System/currentTimeMillis)
        timeout-ms (* timeout-seconds 1000)]
    (loop []
      (let [current-time (System/currentTimeMillis)
            elapsed (- current-time start-time)]
        (if (> elapsed timeout-ms)
          (do
            (println "⏰ Timeout waiting for response")
            false)
          (do
            (Thread/sleep 1000)
            (let [terminal-content (try 
                                    (e/get-element-text driver {:css ".xterm-rows"})
                                    (catch Exception e ""))]
              (if (and (not (empty? terminal-content))
                       (or (.contains terminal-content "●")
                           (.contains terminal-content "✅")
                           (.contains terminal-content "Answer:")
                           (.contains terminal-content "Response:")))
                (do
                  (println "✅ Response detected in terminal")
                  true)
                (recur)))))))))

(defn test-qa-interaction []
  "Test complete Q&A interaction through browser terminal"
  (println "🧪 TESTING COMPLETE Q&A INTERACTION")
  (println "===================================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (println "🌐 Opening browser terminal interface...")
      (e/go driver "http://localhost:9090/web/terminal.html")
      
      ;; Wait for page to load and WebSocket connection
      (Thread/sleep 5000)
      
      ;; Take initial screenshot
      (let [timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")
            screenshot-path (str "screenshots/qa-test-initial_" 
                                (.format timestamp (java.time.LocalDateTime/now)) ".png")]
        (println (str "📸 Taking initial screenshot: " screenshot-path))
        (e/screenshot driver screenshot-path))
      
      ;; Check connection status
      (let [status-text (e/get-element-text driver {:css "#statusText"})]
        (println (str "🔌 Connection status: " status-text))
        
        (if (.contains status-text "Connected")
          (do
            (println "✅ WebSocket connection confirmed")
            
            ;; Click on terminal to focus
            (println "🎯 Focusing on terminal...")
            (e/click driver {:css ".xterm-screen"})
            (Thread/sleep 1000)
            
            ;; Ask a simple question using JavaScript
            (let [test-question "What is 2 + 2?"
                  timestamp (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")]
              
              (println (str "❓ Asking question: " test-question))
              
              ;; Send question via JavaScript to terminal
              (e/js-execute driver 
                (str "
                  // Send question to terminal
                  const question = '" test-question "';
                  const terminal = window.term;
                  
                  if (terminal && terminal.onData) {
                    // Type each character
                    for (let i = 0; i < question.length; i++) {
                      terminal.onData(question[i]);
                    }
                    // Press Enter
                    terminal.onData('\\r');
                    console.log('Question sent: ' + question);
                  } else {
                    console.log('Terminal not available');
                  }
                "))
              
              (println "📤 Question sent to terminal")
              
              ;; Wait for response
              (println "⏳ Waiting for Q to process and respond...")
              (if (wait-for-response driver 30)
                (do
                  ;; Take screenshot after response
                  (let [screenshot-path (str "screenshots/qa-test-response_" 
                                            (.format timestamp (java.time.LocalDateTime/now)) ".png")]
                    (println (str "📸 Taking response screenshot: " screenshot-path))
                    (e/screenshot driver screenshot-path))
                  
                  ;; Get terminal content
                  (let [terminal-content (try 
                                          (e/get-element-text driver {:css ".xterm-rows"})
                                          (catch Exception e "Could not read terminal"))]
                    (println "📋 Terminal content after Q&A:")
                    (println terminal-content)
                    
                    ;; Check if we got a meaningful response
                    (if (or (.contains terminal-content "4")
                            (.contains terminal-content "Answer")
                            (.contains terminal-content "●"))
                      (do
                        (println "✅ Q&A INTERACTION TEST PASSED!")
                        (println "🎉 Successfully asked question and received answer!")
                        true)
                      (do
                        (println "❌ No clear answer detected")
                        false))))
                (do
                  (println "❌ Q&A INTERACTION TEST FAILED - No response received")
                  false)))
            
            ;; Also check what happened in the tmux session
            (println "\n🔍 Checking tmux session for comparison...")
            (let [tmux-result (shell/sh "tmux" "capture-pane" "-t" "qq-default" "-p")]
              (when (= 0 (:exit tmux-result))
                (println "📋 Current tmux session content:")
                (println (:out tmux-result)))))
          
          (do
            (println "❌ WebSocket not connected - cannot test Q&A")
            false)))
      
      (catch Exception e
        (println (str "❌ Q&A test failed with exception: " (.getMessage e)))
        false)
      
      (finally
        (Thread/sleep 3000) ; Keep browser open to see results
        (e/quit driver)))))
