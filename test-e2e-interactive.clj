#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[clojure.string :as str])

(defn test-interactive-tui []
  (println "🧪 Testing interactive TUI in dedicated tmux session...")
  
  ; Create test session and run bb qq
  (p/shell "tmux" "new-session" "-d" "-s" "test-interactive-tui" 
           "bash" "-c" "cd . && bb qq")
  
  (Thread/sleep 3000) ; Wait for TUI to load
  
  (println "📺 Step 1: TUI should be running, sending filter text...")
  ; Send some characters to filter
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "q" "q")
  (Thread/sleep 1000)
  
  (println "📺 Step 2: Testing Ctrl+P navigation...")
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "C-p")
  (Thread/sleep 500)
  
  (println "📺 Step 3: Testing Ctrl+N navigation...")
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "C-n")
  (Thread/sleep 500)
  
  (println "📺 Step 4: Testing backspace...")
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "BSpace")
  (Thread/sleep 500)
  
  (println "📺 Step 5: Testing escape to clear...")
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "Escape")
  (Thread/sleep 500)
  
  ; Capture final state
  (let [content (-> (p/shell {:out :string} "tmux" "capture-pane" "-t" "test-interactive-tui" "-p")
                    :out
                    str/trim)]
    (println "📺 Final TUI state:")
    (println content))
  
  (println "\n🚪 Exiting TUI...")
  (p/shell "tmux" "send-keys" "-t" "test-interactive-tui" "q")
  (Thread/sleep 1000)
  
  ; Clean up
  (p/shell "tmux" "kill-session" "-t" "test-interactive-tui")
  
  (println "\n✅ Interactive test completed!")
  (println "📋 Manual verification needed:")
  (println "   - Filter text appeared when typing")
  (println "   - Ctrl+P/Ctrl+N navigation worked")
  (println "   - Backspace removed characters")
  (println "   - Escape cleared filter")
  (println "\n🎯 To manually test: tmux new-session -s manual-test 'cd . && bb qq'"))

(test-interactive-tui)
