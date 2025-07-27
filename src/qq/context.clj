(ns qq.context
  "Context summarization using vacant Q windows"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [qq.tmux :as tmux]
            [qq.session :as session]))

;; Configuration
(def ^:private QQ-DIR (str (System/getProperty "user.home") "/.knock/qq"))

;; Context Management

(defn- get-context-file [session-id]
  "Get context file path for session"
  (str QQ-DIR "/sessions/" session-id "/context.json"))

(defn- load-context [session-id]
  "Load context summary from disk"
  (let [context-file (get-context-file session-id)]
    (if (.exists (io/file context-file))
      (try
        (json/read-str (slurp context-file) :key-fn keyword)
        (catch Exception e
          (println "Warning: Could not load context for session" session-id ":" (.getMessage e))
          {:summary "Context unavailable" :last-updated 0 :themes []}))
      {:summary "No context summary yet" :last-updated 0 :themes []})))

(defn- save-context [session-id context-data]
  "Save context summary to disk"
  (let [context-file (get-context-file session-id)]
    (try
      (spit context-file (json/write-str context-data {:pretty true}))
      (catch Exception e
        (println "Warning: Could not save context for session" session-id ":" (.getMessage e))))))

(defn summarize-session [session-id]
  "Generate context summary for a session using its own Q window"
  (try
    (println (str "🔄 Generating context summary for session " session-id "..."))
    
    ;; Capture current conversation from tmux
    (let [conversation (tmux/capture-output session-id)]
      (if (str/blank? conversation)
        (println "⚠️  No conversation content to summarize")
        
        ;; Use the session's own Q window to generate summary
        (let [summary-prompt (str "Please provide a brief summary of our conversation so far. 
                                 Focus on:
                                 1. Main topics discussed (2-3 key themes)
                                 2. Current context/focus area
                                 3. Any ongoing tasks or questions
                                 
                                 Keep it concise (under 200 words) and structured.
                                 
                                 Format as:
                                 **Summary:** [brief overview]
                                 **Key Themes:** [theme1, theme2, theme3]
                                 **Current Focus:** [what we're working on now]")
              
              response (tmux/send-and-wait session-id summary-prompt)]
          
          ;; Parse and structure the response
          (let [summary-data {:summary (str/trim response)
                             :last-updated (System/currentTimeMillis)
                             :conversation-length (count conversation)
                             :themes (extract-themes response)}]
            
            ;; Save context summary
            (save-context session-id summary-data)
            (println "✅ Context summary updated")
            summary-data))))
    
    (catch Exception e
      (println "Error generating context summary:" (.getMessage e))
      {:summary "Error generating summary" :last-updated (System/currentTimeMillis) :themes []})))

(defn- extract-themes [summary-text]
  "Extract key themes from summary text"
  (try
    ;; Look for "Key Themes:" section and extract themes
    (let [lines (str/split-lines summary-text)
          themes-line (first (filter #(str/includes? (str/lower-case %) "key themes") lines))]
      (if themes-line
        ;; Extract themes from the line
        (let [themes-part (str/replace themes-line #"(?i).*key themes:?\s*" "")
              themes (-> themes-part
                        (str/split #"[,\n]")
                        (->> (map str/trim)
                             (remove str/blank?)
                             (take 5)))]  ; Limit to 5 themes
          themes)
        []))
    (catch Exception e
      (println "Warning: Could not extract themes:" (.getMessage e))
      [])))

(defn get-session-context [session-id]
  "Get current context summary for a session"
  (load-context session-id))

(defn should-update-context? [session-id]
  "Determine if context should be updated based on activity"
  (let [session (session/load session-id)
        context (load-context session-id)
        last-activity (:last-activity session 0)
        last-context-update (:last-updated context 0)
        time-since-update (- (System/currentTimeMillis) last-context-update)
        messages-since-update (- (:message-count session 0) 
                                (:message-count context 0))]
    
    ;; Update if:
    ;; - Never updated before
    ;; - More than 1 hour since last update AND more than 5 messages
    ;; - More than 10 messages since last update
    (or (zero? last-context-update)
        (and (> time-since-update (* 60 60 1000))  ; 1 hour
             (> messages-since-update 5))
        (> messages-since-update 10))))

(defn auto-update-context [session-id]
  "Automatically update context if needed"
  (when (should-update-context? session-id)
    (println (str "🔄 Auto-updating context for session " session-id))
    (summarize-session session-id)))

(defn list-session-themes []
  "List all themes across all sessions"
  (let [sessions (session/list-all)
        all-themes (mapcat (fn [session]
                            (let [context (load-context (:id session))]
                              (map #(hash-map :theme % :session (:name session))
                                   (:themes context))))
                          sessions)]
    (group-by :theme all-themes)))
