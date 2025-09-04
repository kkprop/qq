(ns qq.simple-watcher
  "Simple watcher using tail -F approach from rt-ops"
  (:require [babashka.process :as proc]
            [clojure.core.async :refer [chan go >! <! close!]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn ->ch [stream]
  "Convert stream to core.async channel"
  (let [c (chan)]
    (go
      (with-open [reader (io/reader stream)]
        (doseq [ln (line-seq reader)]
          (>! c ln)))
      (close! c))
    c))

(defn tail-f [path]
  "Tail -F a file and return channel of lines"
  (->ch (:out (proc/process ["tail" "-n0" "-F" path]))))

(defn extract-questions [line]
  "Extract question from line if it's user input"
  (when (str/ends-with? line "\u001B[?2004l")
    (-> line
        (str/replace #"\u001B\[[0-9;]*m" "")
        (str/replace #"\u001B\[\?[0-9]+[lh]" "")
        (str/replace #"\r" "")
        (str/replace #"\u001B\[K" "")
        (str/replace #"\u001B\[[0-9]+C" "")
        (str/replace #"> \u001B\[2C" "")
        (str/trim))))

(defn log-question [session-name question]
  "Log question to JSONL"
  (let [session-dir (str (System/getProperty "user.home") 
                        "/.knock/qq/sessions/" session-name)
        timeline-file (str session-dir "/timeline.jsonl")
        entry {:timestamp (str (java.time.Instant/now))
               :type "question" 
               :content question
               :user "human"}]
    (io/make-parents timeline-file)
    (spit timeline-file (str (json/write-str entry) "\n") :append true)
    (println "📝 Logged:" question)))

(defn watch-session [session-name]
  "Watch a session using simple tail -F approach"
  (let [stream-file (str "/tmp/qq-stream-" session-name ".log")
        lines-chan (tail-f stream-file)]
    (println "🔄 Starting simple watcher for" session-name)
    (go
      (while true
        (when-let [line (<! lines-chan)]
          (when-let [question (extract-questions line)]
            (when (> (count question) 1)
              (log-question session-name question))))))))

(defn test-simple-watcher []
  "Test the simple watcher"
  (println "🧪 Testing simple watcher...")
  (watch-session "qq-q")
  (println "✅ Simple watcher started for qq-q")
  (println "📊 Add test content and check results..."))
