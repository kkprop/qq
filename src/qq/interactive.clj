(ns qq.interactive
  "Interactive session selector using qq.tui"
  (:require [qq.tui :as tui]
            [qq.tmux :as tmux]
            [clojure.string :as str]
            [babashka.process :as process]
            [babashka.nrepl-client :as nrepl]))

(defn format-session [session selected?]
  (let [prefix (if selected? "► " "  ")
        status (if (:active session) "🟢" "⚪")
        name (:name session)
        panes (str "(" (:panes session) " panes)")]
    (str prefix status " " name " " panes)))

(defn render-sessions [state]
  (let [{:keys [items selected query cursor-visible]} state
        filtered-items (tui/filter-items (or query "") items)
        header "🎯 QQ TMUX SESSIONS\r\n========================\r\n"
        cursor (if cursor-visible "█" " ")
        search-line (str "🔍 Filter: " (or query "") cursor "\r\n")
        sessions (str/join "\r\n" 
                   (map-indexed 
                     (fn [idx session]
                       (format-session session (= idx selected)))
                     filtered-items))
        footer "\r\n\r\n↑↓/Ctrl+P/Ctrl+N/Ctrl+K Navigate | Esc Clear | Enter Select | Backspace Delete | Ctrl+C Quit"]
    (str header search-line sessions footer)))

(defn get-tmux-sessions []
  (try
    (let [result (babashka.process/shell {:out :string :continue true} "tmux" "list-sessions" "-F" "#{session_name}:#{session_windows}:#{session_attached}")
          lines (str/split-lines (:out result))]
      (if (zero? (:exit result))
        (map (fn [line]
               (let [[name windows attached] (str/split line #":")]
                 {:name name
                  :active (= attached "1")
                  :panes (Integer/parseInt windows)}))
             (filter #(not (str/blank? %)) lines))
        []))
    (catch Exception e
      (println "Error getting tmux sessions:" (.getMessage e))
      [])))

(defn attach-session [session]
  (if (System/getenv "TMUX")
    ; We're already inside tmux - provide instructions instead of attaching
    (do
      (println (str "🔗 Session: " (:name session)))
      (println (str "📋 To attach from outside tmux, run: tmux attach-session -t " (:name session)))
      (println "⚠️  Cannot attach from within tmux session"))
    ; We're not in tmux - safe to attach
    (do
      (println (str "🔗 Attaching to session: " (:name session)))
      (try
        (let [result (process/shell {:inherit true} "tmux" "attach-session" "-t" (:name session))]
          (if (zero? (:exit result))
            (println "✅ Session attached successfully")
            (println "❌ Failed to attach to session")))
        (catch Exception e
          (println "❌ Error attaching to session:" (.getMessage e)))))))

(defn decorate-session-name [name]
  "Add qq- prefix if not already present"
  (if (str/starts-with? name "qq-")
    name
    (str "qq-" name)))

(defn session-exists? [session-name]
  "Check if tmux session exists"
  (try
    (let [result (process/shell {:out :string :continue true} "tmux" "has-session" "-t" session-name)]
      (zero? (:exit result)))
    (catch Exception e false)))

(defn auto-add-to-watcher [session-name]
  "Automatically add session to watcher if daemon is running"
  (try
    (let [result (nrepl/eval-expr {:host "127.0.0.1" :port 7888 
                                  :expr (str "(qq.watcher/add-session \"" session-name "\")")})]
      (println "📝 Auto-added" session-name "to watcher"))
    (catch Exception e
      (println "⚠️ Watcher daemon not running - start with 'bb watcher' for logging"))))

(defn create-or-attach-session [session-name]
  "Create session if it doesn't exist, then attach"
  (let [decorated-name (decorate-session-name session-name)]
    (if (System/getenv "TMUX")
      ; Inside tmux - show instructions
      (do
        (println (str "🔗 Session: " decorated-name))
        (if (session-exists? decorated-name)
          (println (str "📋 To attach: tmux attach-session -t " decorated-name))
          (println (str "📋 To create: tmux new-session -s " decorated-name " q chat")))
        (println "⚠️  Cannot attach from within tmux session"))
      ; Outside tmux - create or attach
      (do
        (if (session-exists? decorated-name)
          (println (str "🔗 Attaching to existing session: " decorated-name))
          (println (str "✨ Creating new session: " decorated-name)))
        (try
          ; Auto-add to watcher before creating/attaching
          (auto-add-to-watcher decorated-name)
          (let [result (process/shell {:inherit true} "tmux" "new-session" "-A" "-s" decorated-name "-c" (System/getProperty "user.dir") "q" "chat")]
            (if (zero? (:exit result))
              (println "✅ Session ready")
              (println "❌ Failed to create/attach session")))
          (catch Exception e
            (println "❌ Error:" (.getMessage e))))))))

(defn qq-interactive-with-args [args]
  "Enhanced qq command with smart session management"
  (if (empty? args)
    ; No args - show interactive selector with "Create New" option
    (let [sessions (get-tmux-sessions)
          ; Add "Create New" option to the list
          options (conj sessions {:name "➕ Create New Session" :special :create-new :active false :panes 0})
          result (tui/select-from 
                   options
                   :title "QQ TMUX SESSIONS"
                   :item-fn (fn [session]
                             (if (= (:special session) :create-new)
                               "➕ Create New Session"
                               (let [status (if (:active session) "🟢" "⚪")
                                     name (:name session)
                                     panes (str "(" (:panes session) " panes)")]
                                 (str status " " name " " panes)))))]
      (when result
        (if (= (:special result) :create-new)
          ; Handle create new session
          (do
            (print "Enter new session name: ")
            (flush)
            (let [new-name (read-line)]
              (when (not (str/blank? new-name))
                (create-or-attach-session new-name))))
          ; Handle existing session selection
          (attach-session result))))
    ; Args provided - smart create or attach
    (create-or-attach-session (first args))))
