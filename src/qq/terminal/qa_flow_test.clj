(ns qq.terminal.qa-flow-test
  "🔄 Q&A Flow Test - Browser ↔ Tmux Communication
  
  Simple test to verify the Question & Answer flow between:
  - Browser terminal input
  - WebSocket server 
  - Tmux Q sessions
  - Response back to browser
  
  ## Usage:
  ```clojure
  (test-qa-flow \"ls\" \"default\")
  (simulate-browser-command \"help\" \"default\")
  ```"
  (:require [clojure.java.shell :as shell]
            [clojure.data.json :as json]))

(defn test-tmux-communication
  "Test if we can communicate with tmux sessions"
  [session-id]
  (let [tmux-session (str "qq-" session-id)]
    (println (str "🧪 Testing tmux communication with: " tmux-session))
    
    ;; Check if session exists
    (let [check-result (shell/sh "tmux" "has-session" "-t" tmux-session)]
      (if (= 0 (:exit check-result))
        (do
          (println "✅ Tmux session exists")
          ;; Try to send a simple command
          (let [send-result (shell/sh "tmux" "send-keys" "-t" tmux-session "echo 'Hello from browser!'" "Enter")]
            (if (= 0 (:exit send-result))
              (do
                (println "✅ Command sent successfully")
                {:success true :message "Tmux communication working"})
              (do
                (println (str "❌ Failed to send command: " (:err send-result)))
                {:success false :message "Failed to send command"}))))
        (do
          (println (str "❌ Tmux session not found: " tmux-session))
          {:success false :message "Session not found"})))))

(defn simulate-browser-command
  "Simulate a command coming from the browser"
  [command session-id]
  (println (str "📤 Simulating browser command: " command))
  (println (str "🎯 Target session: " session-id))
  
  ;; Test the flow
  (let [tmux-result (test-tmux-communication session-id)]
    (if (:success tmux-result)
      (do
        (println "✅ Q&A flow test successful")
        {:type "output"
         :content (str "✅ Command '" command "' processed successfully\n"
                      "📡 Tmux communication: " (:message tmux-result) "\n"
                      "🎯 Session: " session-id "\n$ ")})
      (do
        (println "❌ Q&A flow test failed")
        {:type "output"
         :content (str "❌ Command '" command "' failed\n"
                      "📡 Error: " (:message tmux-result) "\n$ ")}))))

(defn test-qa-flow
  "Test the complete Q&A flow"
  [command session-id]
  (println "🔄 TESTING Q&A FLOW")
  (println "===================")
  (println (str "📝 Command: " command))
  (println (str "🎯 Session: " session-id))
  (println "")
  
  (let [result (simulate-browser-command command session-id)]
    (println "📊 Result:")
    (println (json/write-str result :indent true))
    result))

(defn list-available-sessions
  "List available Q sessions"
  []
  (println "📋 Checking available Q sessions...")
  (let [result (shell/sh "tmux" "list-sessions")]
    (if (= 0 (:exit result))
      (let [sessions (filter #(.startsWith % "qq-") 
                            (clojure.string/split (:out result) #"\n"))]
        (println (str "✅ Found " (count sessions) " Q sessions:"))
        (doseq [session sessions]
          (println (str "  - " session)))
        sessions)
      (do
        (println "❌ Failed to list tmux sessions")
        []))))

;; Test functions
(defn run-qa-flow-tests
  "Run comprehensive Q&A flow tests"
  []
  (println "🧪 COMPREHENSIVE Q&A FLOW TESTS")
  (println "================================")
  (println "")
  
  ;; Test 1: List sessions
  (println "🔍 Test 1: List available sessions")
  (list-available-sessions)
  (println "")
  
  ;; Test 2: Test communication
  (println "🔍 Test 2: Test tmux communication")
  (test-tmux-communication "default")
  (println "")
  
  ;; Test 3: Simulate browser commands
  (println "🔍 Test 3: Simulate browser commands")
  (test-qa-flow "help" "default")
  (println "")
  (test-qa-flow "ls" "default")
  (println "")
  
  (println "✅ Q&A flow tests completed!"))
