(ns qq.core
  "Main API for Amazon Q Session Manager"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [babashka.process :as p]
            [qq.session :as session]
            [qq.tmux :as tmux]
            [qq.naming :as naming]))

;; Configuration
(def ^:private MAX-SESSIONS 21)
(def ^:private QQ-DIR (str (System/getProperty "user.home") "/.knock/qq"))

;; Current session state
(def ^:private current-session (atom nil))

;; Public API Functions

(defn create-session
  "Create a new Q session with initial context"
  [{:keys [context]}]
  (when (>= (count (session/list-all)) MAX-SESSIONS)
    (println (str "⚠️  Warning: Approaching " MAX-SESSIONS " session limit. Performance may be affected.")))
  
  (let [session-id (session/generate-id)
        session-data {:id session-id
                      :context context
                      :created-at (System/currentTimeMillis)
                      :last-activity (System/currentTimeMillis)
                      :message-count 0}]
    
    ;; Create tmux session
    (println "🚀 Creating tmux session...")
    (let [tmux-result (tmux/create-session session-id)]
      (if (:success tmux-result)
        (do
          (println "✅ Tmux session created")
          ;; Generate terse name using naming service
          (println "🏷️  Generating session name...")
          (let [terse-name (naming/generate-name context)]
            (println (str "✅ Generated name: " terse-name))
            (let [updated-session (assoc session-data :name terse-name)]
              ;; Save session metadata
              (session/save updated-session)
              ;; Set as current session
              (reset! current-session session-id)
              ;; Return session info
              (println (str "🎉 Session created successfully!"))
              {:session-id session-id :name terse-name})))
        (do
          (println "❌ Failed to create tmux session")
          {:error "Failed to create tmux session"})))))

(defn get-active-session []
  "Get the session to use for commands - current session or default"
  (or @current-session
      (session/ensure-default-session)))

(defn ask
  "Ask a question to current or specified session (sync)"
  ([question]
   (let [session-id (get-active-session)]
     (ask session-id question)))
  ([session-name-or-id question]
   (let [session-id (if (= session-name-or-id "default")
                     "default"
                     (session/resolve-name session-name-or-id))]
     (if session-id
       (do
         ;; Create tmux session if it doesn't exist (for default session)
         (when (and (= session-id "default") 
                   (not (tmux/session-exists? session-id)))
           (println "🚀 Starting default Q session...")
           (tmux/create-session session-id)
           (println "✅ Default Q session ready"))
         
         ;; Update last activity
         (session/update-activity session-id)
         ;; Send question to tmux session and get response (timeline logging happens in tmux)
         (let [response (tmux/send-and-wait session-id question)]
           ;; Update default session context if this was the default session
           (when (= session-id "default")
             (session/update-default-context))
           response))
       (println (str "❌ Session not found: " session-name-or-id))))))

(defn ask-async
  "Ask a question asynchronously, returns a promise"
  ([question]
   (ask-async @current-session question))
  ([session-name-or-id question]
   (let [session-id (session/resolve-name session-name-or-id)]
     (if session-id
       (do
         (session/update-activity session-id)
         (tmux/send-async session-id question))
       (println (str "❌ Session not found: " session-name-or-id))))))

(defn list-sessions
  "List all sessions with context summaries"
  []
  (let [sessions (session/list-all)]
    (if (empty? sessions)
      (println "📋 No Q sessions found. Use: bb ask \"your question\" to start with default session")
      (do
        (println "📋 Amazon Q Sessions:")
        (println "=====================")
        (doseq [session sessions]
          (let [current-marker (if (= (:id session) @current-session) "→ " "  ")
                default-marker (if (:is-default session) "* " "  ")
                age (session/format-age (:last-activity session))
                context-preview (session/truncate-context (:context session) 60)]
            (println (str default-marker current-marker (:name session) " (" (:message-count session) " msgs, " age ")"))
            (println (str "    " context-preview))))
        (println)
        (println "Legend: * = default session, → = current session")))))

(defn attach-session
  "Get tmux attach command for a session"
  [session-name-or-id]
  (let [session-id (session/resolve-name session-name-or-id)]
    (if session-id
      (let [session (session/load session-id)
            tmux-name (tmux/session-name (:name session))]
        (println (str "tmux attach -t " tmux-name)))
      (println (str "❌ Session not found: " session-name-or-id)))))

(defn switch-to-default
  "Switch current session to default"
  []
  (let [default-id (session/ensure-default-session)]
    (reset! current-session default-id)
    (println "✅ Switched to default session")))

(defn switch-to
  "Switch current session"
  [session-name-or-id]
  (if (= session-name-or-id "default")
    (switch-to-default)
    (let [session-id (session/resolve-name session-name-or-id)]
      (if session-id
        (do
          (reset! current-session session-id)
          (let [session (session/load session-id)]
            (println (str "✅ Switched to session: " (:name session)))))
        (println (str "❌ Session not found: " session-name-or-id))))))

;; CLI Entry Point
(defn -main [& args]
  (case (first args)
    "create" (let [context (second args)]
               (if context
                 (create-session {:context context})
                 (println "Usage: bb create \"your context description\"")))
    
    "ask" (let [question (if (= (count args) 2)
                          (second args)
                          (str/join " " (rest args)))]
            (if question
              (ask question)
              (println "Usage: bb ask \"your question\"")))
    
    "list" (list-sessions)
    
    "attach" (let [session-name (second args)]
               (if session-name
                 (attach-session session-name)
                 (println "Usage: bb attach session-name")))
    
    "switch" (let [session-name (second args)]
               (if session-name
                 (switch-to session-name)
                 (println "Usage: bb switch session-name")))
    
    "switch-default" (switch-to-default)
    
    ;; Default help
    (do
      (println "QQ - Amazon Q Session Manager")
      (println "Usage:")
      (println "  bb ask \"question\"               - Ask question (uses current or default session)")
      (println "  bb create \"context description\"  - Create new named session")
      (println "  bb list                          - List all sessions with summaries")
      (println "  bb switch session-name          - Switch to named session")
      (println "  bb switch-default                - Switch to default session")
      (println "  bb attach session-name          - Get tmux attach command"))))
