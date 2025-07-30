#!/usr/bin/env bb

(ns context-demo
  "Demonstration of context monitoring for tmux windows"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [babashka.process :as p]))

;; ============================================================================
;; CONTEXT DETECTION FUNCTIONS
;; ============================================================================

(defn detect-context-addition [content]
  "Detect /context add commands in tmux window content"
  (let [lines (str/split-lines content)
        context-lines (filter #(str/includes? % "/context add") lines)]
    (when (seq context-lines)
      {:detected true
       :count (count context-lines)
       :examples (take 3 context-lines)
       :timestamp (System/currentTimeMillis)})))

(defn extract-context-text [context-line]
  "Extract the actual context text from /context add command"
  (when (str/includes? context-line "/context add")
    (let [context-part (str/replace context-line #".*?/context add\s*" "")
          cleaned (str/trim context-part)]
      (when-not (str/blank? cleaned)
        cleaned))))

(defn capture-window-content [session-name window-index]
  "Capture content from a tmux window"
  (try
    (let [result (p/shell {:out :string :err :string}
                         "tmux" "capture-pane" "-t" (str session-name ":" window-index) "-p")]
      (if (zero? (:exit result))
        (:out result)
        nil))
    (catch Exception e
      (println (str "❌ Error capturing content: " (.getMessage e)))
      nil)))

;; ============================================================================
;; CONTEXT STORAGE FUNCTIONS
;; ============================================================================

(defn get-context-file [session-name]
  "Get context file path for a session"
  (let [qq-dir (str (System/getProperty "user.home") "/.knock/qq")
        session-id (if (str/starts-with? session-name "qq-")
                     (str/replace session-name #"^qq-" "")
                     session-name)]
    (str qq-dir "/sessions/" session-id "/context.json")))

(defn save-context-addition [session-name context-text]
  "Save a context addition to the session file"
  (try
    (let [context-file (get-context-file session-name)
          context-dir (.getParent (java.io.File. context-file))]
      
      ;; Ensure directory exists
      (.mkdirs (java.io.File. context-dir))
      
      ;; Load existing context or create new
      (let [existing (if (.exists (java.io.File. context-file))
                      (try
                        (json/read-str (slurp context-file) :key-fn keyword)
                        (catch Exception e
                          {:context-additions []}))
                      {:context-additions []})
            
            ;; Add new context
            new-addition {:text context-text
                         :timestamp (System/currentTimeMillis)
                         :session session-name}
            
            updated (update existing :context-additions conj new-addition)]
        
        ;; Save updated context
        (spit context-file (json/write-str updated {:pretty true}))
        (println (str "💾 Saved context addition for " session-name))
        new-addition))
    
    (catch Exception e
      (println (str "❌ Error saving context: " (.getMessage e)))
      nil)))

;; ============================================================================
;; DEMONSTRATION FUNCTIONS
;; ============================================================================

(defn demo-context-detection [session-name window-index]
  "Demonstrate context detection for a specific window"
  (println (str "🔍 CONTEXT DETECTION DEMO: " session-name ":" window-index))
  (println "================================================")
  
  (let [content (capture-window-content session-name window-index)]
    (if content
      (let [detection (detect-context-addition content)]
        (if detection
          (do
            (println (str "✅ Found " (:count detection) " context additions:"))
            (doseq [[idx example] (map-indexed vector (:examples detection))]
              (println (str "  " (inc idx) ". " example))
              (let [context-text (extract-context-text example)]
                (when context-text
                  (println (str "     → Context: " context-text))
                  (save-context-addition session-name context-text))))
            (println))
          (println "❌ No /context add commands detected")))
      (println "❌ Could not capture window content"))))

(defn demo-all-q-sessions []
  "Demonstrate context detection across all Q sessions"
  (println "🤖 CONTEXT DETECTION FOR ALL Q SESSIONS")
  (println "=======================================")
  
  (let [sessions-result (p/shell {:out :string :err :string} 
                                "tmux list-sessions -F '#{session_name}'")]
    (if (zero? (:exit sessions-result))
      (let [all-sessions (str/split-lines (:out sessions-result))
            q-sessions (filter #(str/includes? % "qq") all-sessions)]
        
        (if (empty? q-sessions)
          (println "❌ No Q sessions found")
          (doseq [session q-sessions]
            (demo-context-detection session 1)
            (println))))
      (println "❌ Could not list tmux sessions"))))

(defn show-saved-contexts []
  "Show all saved context additions"
  (println "📋 SAVED CONTEXT ADDITIONS")
  (println "==========================")
  
  (let [qq-dir (str (System/getProperty "user.home") "/.knock/qq/sessions")
        sessions-dir (java.io.File. qq-dir)]
    
    (if (.exists sessions-dir)
      (let [session-dirs (.listFiles sessions-dir)]
        (doseq [session-dir session-dirs]
          (let [context-file (java.io.File. session-dir "context.json")]
            (when (.exists context-file)
              (try
                (let [context-data (json/read-str (slurp context-file) :key-fn keyword)
                      additions (:context-additions context-data [])]
                  (when (seq additions)
                    (println (str "📺 Session: " (.getName session-dir)))
                    (doseq [[idx addition] (map-indexed vector (take-last 3 additions))]
                      (println (str "  " (inc idx) ". " (java.util.Date. (:timestamp addition))))
                      (println (str "     " (:text addition))))
                    (println)))
                (catch Exception e
                  (println (str "❌ Error reading " context-file ": " (.getMessage e))))))))
      (println "❌ No sessions directory found"))))

;; ============================================================================
;; MAIN DEMO FUNCTION
;; ============================================================================

(defn main [& args]
  (let [command (first args)]
    (case command
      "detect"
      (if-let [target (second args)]
        (let [[session window] (str/split target #":")]
          (demo-context-detection session (Integer/parseInt (or window "1"))))
        (demo-all-q-sessions))
      
      "show"
      (show-saved-contexts)
      
      "help"
      (do
        (println "🔍 CONTEXT MONITORING DEMO")
        (println "==========================")
        (println "Commands:")
        (println "  detect [session:window] - Detect context additions")
        (println "  show                    - Show saved contexts")
        (println "  help                    - Show this help"))
      
      (do
        (println "🎯 CONTEXT MONITORING DEMONSTRATION")
        (println "===================================")
        (demo-all-q-sessions)
        (println)
        (show-saved-contexts)))))

(apply main *command-line-args*)
