#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[clojure.string :as str])

(defn tmux-cmd [& args]
  "Execute tmux command and return result"
  (let [result (apply p/shell {:out :string :err :string} "tmux" args)]
    (if (zero? (:exit result))
      (:out result)
      (throw (ex-info "tmux command failed" {:args args :result result})))))

(defn send-keys [session & keys]
  "Send keys to tmux session"
  (apply tmux-cmd "send-keys" "-t" session keys))

(defn capture-pane [session]
  "Capture tmux pane content"
  (str/trim (tmux-cmd "capture-pane" "-t" session "-p")))

(defn test-bb-qq []
  (println "🧪 Starting end-to-end test of bb qq...")
  
  ; Create test session
  (tmux-cmd "new-session" "-d" "-s" "e2e-test-qq")
  (Thread/sleep 1000)
  
  ; Start bb qq
  (send-keys "e2e-test-qq" "cd /Users/dc/kkprop/qq && bb qq" "Enter")
  (Thread/sleep 2000)
  
  (println "📺 Initial screen:")
  (println (capture-pane "e2e-test-qq"))
  
  ; Test 1: Type characters for filtering
  (println "\n🔤 Test 1: Typing 'qq' to filter...")
  (send-keys "e2e-test-qq" "q")
  (Thread/sleep 500)
  (send-keys "e2e-test-qq" "q")
  (Thread/sleep 500)
  
  (println "📺 After typing 'qq':")
  (println (capture-pane "e2e-test-qq"))
  
  ; Test 2: Test Ctrl+P (up navigation)
  (println "\n⬆️ Test 2: Testing Ctrl+P navigation...")
  (send-keys "e2e-test-qq" "C-p")
  (Thread/sleep 500)
  
  (println "📺 After Ctrl+P:")
  (println (capture-pane "e2e-test-qq"))
  
  ; Test 3: Test Ctrl+N (down navigation)
  (println "\n⬇️ Test 3: Testing Ctrl+N navigation...")
  (send-keys "e2e-test-qq" "C-n")
  (Thread/sleep 500)
  
  (println "📺 After Ctrl+N:")
  (println (capture-pane "e2e-test-qq"))
  
  ; Test 4: Clear filter with Escape
  (println "\n🧹 Test 4: Testing Escape to clear filter...")
  (send-keys "e2e-test-qq" "Escape")
  (Thread/sleep 500)
  
  (println "📺 After Escape:")
  (println (capture-pane "e2e-test-qq"))
  
  ; Test 5: Backspace functionality
  (println "\n⌫ Test 5: Testing backspace...")
  (send-keys "e2e-test-qq" "t" "e" "s" "t")
  (Thread/sleep 500)
  (send-keys "e2e-test-qq" "BSpace" "BSpace")
  (Thread/sleep 500)
  
  (println "📺 After typing 'test' and 2 backspaces:")
  (println (capture-pane "e2e-test-qq"))
  
  ; Exit gracefully
  (println "\n🚪 Exiting...")
  (send-keys "e2e-test-qq" "q")
  (Thread/sleep 1000)
  
  ; Clean up
  (tmux-cmd "kill-session" "-t" "e2e-test-qq")
  
  (println "\n✅ End-to-end test completed!"))

(test-bb-qq)
