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
  (let [{:keys [items selected]} state
        header "🎯 QQ TMUX SESSIONS\r\n========================\r\n"
        sessions (str/join "\r\n" 
                   (map-indexed 
                     (fn [idx session]
                       (format-session session (= idx selected)))
                     items))
        footer "\r\n\r\n↑↓ Navigate | Enter Select | q Quit"]
    (str header sessions footer)))

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
  (println (str "Attaching to: " (:name session)))
  ; TODO: Implement actual tmux attach
  )

(defn qq-interactive []
  (let [sessions (get-tmux-sessions)
        app (tui/create-app 
              {:items sessions :selected 0}
              render-sessions)]
    
    ; Start background refresh for testing reactive updates
    (future
      (loop []
        (Thread/sleep 2000) ; Refresh every 2 seconds
        (let [new-sessions (get-tmux-sessions)]
          (when (not= new-sessions (:items @(:state app)))
            (swap! (:state app) assoc :items new-sessions)))
        (when @(:running app)
          (recur))))
    
    (let [result (tui/start-app app)]
      (when result
        (attach-session result)))))
