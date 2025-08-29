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
  (println (str "🔗 Attaching to session: " (:name session)))
  (try
    ; Use tmux attach-session command
    (let [result (process/shell {:inherit true} "tmux" "attach-session" "-t" (:name session))]
      (if (zero? (:exit result))
        (println "✅ Session attached successfully")
        (println "❌ Failed to attach to session")))
    (catch Exception e
      (println "❌ Error attaching to session:" (.getMessage e)))))

(defn qq-interactive []
  (let [sessions (get-tmux-sessions)
        app (tui/create-app 
              {:items sessions :selected 0 :query "" :cursor-visible true}
              render-sessions)]
    
    ; Start cursor blinking
    (future
      (loop []
        (Thread/sleep 500) ; Blink every 500ms
        (when @(:running app)
          (swap! (:state app) update :cursor-visible not)
          (recur))))
    
    ; Start background refresh for testing reactive updates
    (future
      (loop []
        (Thread/sleep 2000) ; Refresh every 2 seconds
        (let [new-sessions (get-tmux-sessions)]
          (when (not= new-sessions (:items @(:state app)))
            (swap! (:state app) assoc :items new-sessions)))
        (when @(:running app)
          (recur))))
    
    (let [result (tui/start-filter-app app)]
      (when result
        (attach-session result)))))
