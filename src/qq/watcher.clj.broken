(ns qq.watcher
  "QQ Watcher daemon - direct JSONL logging without external dependencies"
  (:require [babashka.nrepl.server :as nrepl]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

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
       (filter #(str/ends-with? % "\u001B[?2004l"))
       (map #(-> %
                (str/replace #"\u001B\[[0-9;]*m" "")
                (str/replace #"\u001B\[\?[0-9]+[lh]" "")
                (str/replace #"\r" "")
                (str/replace #"\u001B\[K" "")
                (str/replace #"\u001B\[[0-9]+C" "")
                (str/replace #"> \u001B\[2C" "")
                (str/trim)))
       (filter #(and (> (count %) 1)
                     (not (str/includes? % "\u001B"))))
       (distinct)))

(defn log-question-direct [session-name question]
  "Log question directly to JSONL - self-contained"
  (let [session-dir (str (System/getProperty "user.home") 
                        "/.knock/qq/sessions/" session-name)
        timeline-file (str session-dir "/timeline.jsonl")
        entry {:timestamp (str (java.time.Instant/now))
               :type "question" 
               :content question
               :user "human"}]
    (io/make-parents timeline-file)
    (spit timeline-file (str (json/write-str entry) "\n") :append true)
    (println "📝 Direct JSONL logged:" question)))

(defn start-direct-watcher [session-name]
  "Start direct JSONL watcher with simple polling"
  (let [stream-file (str "/tmp/qq-stream-" session-name ".log")
        watcher-future 
        (future
          (let [last-size (atom 0)]
            (println "🔄 Direct watcher loop starting for" session-name)
            (try
              (while true
                (try
                  (let [file (java.io.File. stream-file)]
                    (when (.exists file)
                      (let [current-size (.length file)]
                        (when (> current-size @last-size)
                          (println "📊 File grew for" session-name "from" @last-size "to" current-size)
                          (let [new-content (with-open [raf (java.io.RandomAccessFile. stream-file "r")]
                                              (.seek raf @last-size)
                                              (let [buffer (byte-array (- current-size @last-size))]
                                                (.read raf buffer)
                                                (String. buffer "UTF-8")))]
                            (println "📝 Processing" (count new-content) "bytes")
                            (let [questions (extract-human-questions new-content)]
                              (println "📝 Found" (count questions) "questions:" questions)
                              (doseq [q questions]
                                (when (and q (> (count q) 1))
                                  (println "📝 Logging:" q)
                                  (log-question-direct session-name q)))))
                          (reset! last-size current-size)))))
                  (Thread/sleep 500)
                  (catch Exception e
                    (println "❌ Inner error:" (.getMessage e))
                    (.printStackTrace e)
                    (Thread/sleep 2000)))
              (catch Exception e
                (println "❌ Outer error:" (.getMessage e))
                (.printStackTrace e)))]
    (swap! watcher-state assoc-in [:watchers session-name] watcher-future)
    (println "✅ Direct JSONL watcher started for" session-name)))

(defn add-session [session-name]
  "Add session to direct JSONL watcher"
  (println "📝 Adding session" session-name "to direct JSONL watcher")
  (swap! watcher-state update :sessions conj session-name)
  (try
    (let [stream-file (str "/tmp/qq-stream-" session-name ".log")
          result (babashka.process/shell {:continue true} 
                                        "tmux" "pipe-pane" "-t" session-name 
                                        "-o" (str "cat >> " stream-file))]
      (if (zero? (:exit result))
        (println "✅ Pipe-pane started for" session-name)
        (println "❌ Failed to start pipe-pane:" (:err result))))
    (catch Exception e
      (println "❌ Error starting pipe-pane:" (.getMessage e))))
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

(defn start-watcher []
  "Start watcher daemon with nREPL"
  (println "🔍 Starting qq-watcher daemon with direct JSONL...")
  (nrepl/start-server! {:port 7889 :host "127.0.0.1"})
  (println "📡 nREPL server started on port 7889")
  (println "🎯 Ready to watch sessions with direct JSONL logging")
  (while true
    (Thread/sleep 10000)))
