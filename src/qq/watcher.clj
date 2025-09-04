(ns qq.watcher
  "QQ Watcher daemon - direct JSONL logging without external dependencies"
  (:require [babashka.nrepl.server :as nrepl]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [qq.log :as log]))

(def watcher-state (atom {:sessions #{} :watchers {}}))

(defn strip-ansi [text]
  "Remove ANSI color codes from text"
  (-> text
      (str/replace #"\u001b\[[0-9;]*m" "")
      (str/replace #"\u001b\[\?[0-9]+[lh]" "")
      (str/replace #"\r" "")
      str/trim))

(defn extract-human-questions [output]
  "Extract human questions using simple line-based approach"
  (->> (str/split-lines output)
       (filter #(str/ends-with? % "\u001B[?2004l"))  ; Lines ending with this are user input
       (map #(-> %
                (str/replace #"\u001B\[[0-9;]*m" "")   ; Remove ANSI color codes
                (str/replace #"\u001B\[\?[0-9]+[lh]" "") ; Remove control sequences  
                (str/replace #"\r" "")                 ; Remove carriage returns
                (str/replace #"\u001B\[K" "")          ; Remove clear line
                (str/replace #"\u001B\[[0-9]+C" "")    ; Remove cursor movement
                (str/replace #"> \u001B\[2C" "")       ; Remove prompt artifacts
                (str/trim)))                           ; Trim whitespace
       (filter #(and (> (count %) 1)                  ; Filter out empty/short lines
                     (not (str/includes? % "\u001B")))) ; Remove lines with remaining ANSI
       (distinct)))                                   ; Remove duplicates

(defn log-question-direct [session-name question]
  "Log question directly to JSONL - self-contained"
  (let [session-dir (str "./log/" session-name)
        timeline-file (str session-dir "/timeline.jsonl")
        entry {:timestamp (str (java.time.Instant/now))
               :type "question" 
               :content question
               :user "human"}]
    ;; Ensure session directory exists
    (io/make-parents timeline-file)
    ;; Append to timeline
    (spit timeline-file (str (json/write-str entry) "\n") :append true)
    (log/log-success "Direct JSONL logged:" question)))

(defn start-direct-watcher [session-name]
  "Start direct JSONL watcher with simple polling"
  (let [stream-file (str (System/getProperty "user.dir") "/log/tmp/qq-stream-" session-name ".log")
        watcher-future (future
                         (let [last-size (atom 0)]
                           (println "🔄 Direct watcher loop starting for" session-name)
                           (while true
                             (try
                               (let [file (java.io.File. stream-file)]
                                 (if (.exists file)
                                   (let [current-size (.length file)]

                                     (when (> current-size @last-size)
                                       (println "📊 File grew! Processing" (- current-size @last-size) "new bytes")
                                       (let [new-content (with-open [raf (java.io.RandomAccessFile. stream-file "r")]
                                                           (.seek raf @last-size)
                                                           (let [buffer (byte-array (- current-size @last-size))]
                                                             (.read raf buffer)
                                                             (String. buffer "UTF-8")))]
                                         (println "📝 New content:" (pr-str (subs new-content 0 (min 100 (count new-content)))))
                                         (let [questions (extract-human-questions new-content)]
                                           (println "📝 Extracted" (count questions) "questions:" questions)
                                           (doseq [q questions]
                                             (when (and q (> (count q) 1))
                                               (println "📝 Logging question:" q)
                                               (log-question-direct session-name q)))))
                                       (reset! last-size current-size)))
                                   (println "⚠️ Stream file does not exist:" stream-file)))
                               (Thread/sleep 500) ; Fast polling
                               (catch Exception e
                                 (println "❌ Watcher error for" session-name ":" (.getMessage e))
                                 (.printStackTrace e)
                                 (Thread/sleep 2000))))))]
    (swap! watcher-state assoc-in [:watchers session-name] watcher-future)
    (println "✅ Direct JSONL watcher started for" session-name)))

(defn add-session [session-name]
  "Add session to direct JSONL watcher"
  (println "📝 Adding session" session-name "to direct JSONL watcher")
  (swap! watcher-state update :sessions conj session-name)
  ;; Start tmux pipe-pane for stream capture
  (try
    (let [stream-file (str (System/getProperty "user.dir") "/log/tmp/qq-stream-" session-name ".log")]
      ;; Ensure tmp directory exists
      (io/make-parents stream-file)
      (let [result (babashka.process/shell {:continue true :out :string :err :string} 
                                          "tmux" "pipe-pane" "-t" session-name 
                                          "-o" (str "cat >> " stream-file))]
        (if (zero? (:exit result))
          (println "✅ Pipe-pane started for" session-name)
          (println "❌ Failed to start pipe-pane:" (:err result)))))
    (catch Exception e
      (println "❌ Error starting pipe-pane:" (.getMessage e))))
  ;; Start direct watcher
  (start-direct-watcher session-name)
  {:status :added :session session-name})

(defn remove-session [session-name]
  "Remove session from watcher"
  (swap! watcher-state update :sessions disj session-name)
  (when-let [watcher-future (get-in @watcher-state [:watchers session-name])]
    (future-cancel watcher-future))
  (swap! watcher-state update :watchers dissoc session-name)
  {:status :removed :session session-name})

(defn list-sessions []
  "List watched sessions"
  {:sessions (:sessions @watcher-state)})

(defn discover-existing-sessions []
  "Discover all existing qq- tmux sessions and auto-add them"
  (try
    (let [result (babashka.process/shell {:out :string :continue true} "tmux" "list-sessions" "-F" "#{session_name}")
          sessions (when (zero? (:exit result))
                    (->> (str/split-lines (:out result))
                         (filter #(str/starts-with? % "qq-"))
                         (map str/trim)))]
      (println "🔍 Discovered" (count sessions) "existing Q sessions:" sessions)
      (doseq [session sessions]
        (println "📝 Auto-reconnecting to" session)
        (add-session session))
      sessions)
    (catch Exception e
      (println "⚠️ Could not discover existing sessions:" (.getMessage e))
      [])))

(defn start-watcher []
  "Start watcher daemon with nREPL and auto-discover existing sessions"
  (log/log-info "Starting qq-watcher daemon with direct JSONL...")
  (nrepl/start-server! {:port 7888 :host "127.0.0.1"})
  (log/log-success "nREPL server started on port 7888")
  
  ;; Auto-discover and reconnect to existing Q sessions
  (discover-existing-sessions)
  
  (log/log-success "Ready to watch sessions with direct JSONL logging")
  
  ;; Keep daemon alive
  (while true
    (Thread/sleep 10000)))
