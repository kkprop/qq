(ns qq.terminal.command-test
  "🧪 Command Forwarding Test - Test tmux command forwarding directly"
  (:require [clojure.java.shell :as shell]
            [clojure.data.json :as json]))

(defn test-command-forwarding []
  "Test command forwarding to tmux session"
  (println "🧪 TESTING COMMAND FORWARDING")
  (println "==============================")
  
  (let [test-command "echo 'Command forwarding test successful!'"
        session-id "qq-default"]
    
    (println (str "🎯 Testing command: " test-command))
    (println (str "📡 Target session: " session-id))
    
    ;; Send command to tmux
    (let [result (shell/sh "tmux" "send-keys" "-t" session-id test-command "Enter")]
      (if (= 0 (:exit result))
        (do
          (println "✅ Command sent successfully")
          (Thread/sleep 1000) ; Wait for execution
          
          ;; Capture output
          (let [output-result (shell/sh "tmux" "capture-pane" "-t" session-id "-p")]
            (if (= 0 (:exit output-result))
              (do
                (println "📋 Captured output:")
                (println (:out output-result))
                (println "✅ Command forwarding test PASSED"))
              (println "❌ Failed to capture output"))))
        (println (str "❌ Failed to send command: " (:err result)))))))
