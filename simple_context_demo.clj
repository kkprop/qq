#!/usr/bin/env bb

(require '[clojure.string :as str]
         '[babashka.process :as p])

(defn capture-window-content [session-name window-index]
  "Capture content from a tmux window"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "capture-pane" "-t" (str session-name ":" window-index) "-p")]
      (if (zero? (:exit result))
        (:out result)
        nil))
    (catch Exception e
      nil)))

(defn detect-context-additions [content]
  "Detect /context add commands in content"
  (when content
    (let [lines (str/split-lines content)
          context-lines (filter #(str/includes? % "/context add") lines)]
      context-lines)))

(defn demo-context-detection [session-name]
  "Demo context detection for a session"
  (println (str "🔍 Checking " session-name " for /context add commands..."))
  
  (let [content (capture-window-content session-name 1)
        context-additions (detect-context-additions content)]
    
    (if (seq context-additions)
      (do
        (println (str "✅ Found " (count context-additions) " context additions:"))
        (doseq [[idx line] (map-indexed vector context-additions)]
          (println (str "  " (inc idx) ". " line))))
      (println "❌ No /context add commands found"))
    (println)))

(defn main []
  (println "🎯 CONTEXT MONITORING DEMO")
  (println "==========================")
  
  ;; Get Q sessions
  (let [sessions-result (p/shell {:out :string :err :string} 
                                "tmux list-sessions -F '#{session_name}'")]
    (if (zero? (:exit sessions-result))
      (let [all-sessions (str/split-lines (:out sessions-result))
            q-sessions (filter #(str/includes? % "qq") all-sessions)]
        
        (if (empty? q-sessions)
          (println "❌ No Q sessions found")
          (doseq [session q-sessions]
            (demo-context-detection session))))
      (println "❌ Could not list tmux sessions"))))

(main)
