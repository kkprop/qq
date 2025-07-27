(ns qq.core
  "Main API for Amazon Q Session Manager"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [babashka.process :as p]
            [qq.session :as session]
            [qq.tmux :as tmux]
            [qq.naming :as naming]
            [qq.context :as context]))

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
    (tmux/create-session session-id)
    
    ;; Generate terse name using naming service
    (let [terse-name (naming/generate-name context)]
      (let [updated-session (assoc session-data :name terse-name)]
        ;; Save session metadata
        (session/save updated-session)
        ;; Set as current session
        (reset! current-session session-id)
        ;; Return session info
        {:session-id session-id :name terse-name}))))

(defn ask
  "Ask a question to current or specified session (sync)"
  ([question]
   (ask @current-session question))
  ([session-name-or-id question]
   (let [session-id (session/resolve-name session-name-or-id)]
     (if session-id
       (do
         ;; Update last activity
         (session/update-activity session-id)
         ;; Send question to tmux session and get response
         (tmux/send-and-wait session-id question))
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
      (println "📋 No Q sessions found. Create one with: bb create \"your context\"")
      (do
        (println "📋 Amazon Q Sessions:")
        (println "=====================")
        (doseq [session sessions]
          (let [current-marker (if (= (:id session) @current-session) "→ " "  ")
                age (session/format-age (:last-activity session))
                context-preview (session/truncate-context (:context session) 60)]
            (println (str current-marker (:name session) " (" (:message-count session) " msgs, " age ")")
                    (println (str "    " context-preview)))))))))

(defn attach-session
  "Get tmux attach command for a session"
  [session-name-or-id]
  (let [session-id (session/resolve-name session-name-or-id)]
    (if session-id
      (let [session (session/load session-id)
            tmux-name (tmux/session-name (:name session))]
        (println (str "tmux attach -t " tmux-name)))
      (println (str "❌ Session not found: " session-name-or-id)))))

(defn switch-to
  "Switch current session"
  [session-name-or-id]
  (let [session-id (session/resolve-name session-name-or-id)]
    (if session-id
      (do
        (reset! current-session session-id)
        (let [session (session/load session-id)]
          (println (str "✅ Switched to session: " (:name session)))))
      (println (str "❌ Session not found: " session-name-or-id)))))

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
    
    ;; Default help
    (do
      (println "QQ - Amazon Q Session Manager")
      (println "Usage:")
      (println "  bb create \"context description\"  - Create new session")
      (println "  bb ask \"question\"               - Ask current session")
      (println "  bb list                          - List all sessions")
      (println "  bb attach session-name          - Get tmux attach command")
      (println "  bb switch session-name          - Switch current session"))))
