(ns qq.interactive
  "Interactive session selector using qq.tui"
  (:require [qq.tui :as tui]
            [qq.tmux :as tmux]
            [clojure.string :as str]
            [babashka.process :as process]))

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

(defn qq-interactive []
  (let [result (tui/select-from get-tmux-sessions render-sessions)]
    (when result
      (attach-session result))))
