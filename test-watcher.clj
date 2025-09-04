#!/usr/bin/env bb

(require '[clojure.string :as str]
         '[babashka.process :refer [shell]])

(defn test-watcher-capture []
  "Test the watcher capture mechanism step by step"
  
  (println "🧪 Testing watcher JSONL capture mechanism...")
  
  ;; Step 1: Check if watcher is running
  (println "\n📊 Step 1: Check watcher daemon status")
  (let [result (shell {:out :string :continue true} "bash" "-c" "ps aux | grep 'bb watcher' | grep -v grep")]
    (if (and (zero? (:exit result)) (not (empty? (str/trim (:out result)))))
      (println "✅ Watcher daemon is running")
      (println "❌ Watcher daemon not running")))
  
  ;; Step 2: Check stream file exists
  (println "\n📊 Step 2: Check stream file")
  (let [stream-file "/tmp/qq-stream-qq-q.log"]
    (if (.exists (java.io.File. stream-file))
      (do
        (println "✅ Stream file exists:" stream-file)
        (println "📏 Current size:" (.length (java.io.File. stream-file)) "bytes"))
      (println "❌ Stream file does not exist:" stream-file)))
  
  ;; Step 3: Simulate Q input by appending to stream file
  (println "\n📊 Step 3: Simulate Q input")
  (let [stream-file "/tmp/qq-stream-qq-q.log"
        test-input "\u001b[38;5;13m> \u001b[39m\r\u001b[2CTEST WATCHER CAPTURE\u001b[?2004l\n"]
    (spit stream-file test-input :append true)
    (println "✅ Appended test input to stream file"))
  
  ;; Step 4: Wait for watcher to process
  (println "\n📊 Step 4: Wait for watcher processing...")
  (Thread/sleep 2000) ; Wait 2 seconds for polling
  
  ;; Step 5: Check timeline file
  (println "\n📊 Step 5: Check timeline JSONL")
  (let [timeline-file (str (System/getProperty "user.home") "/.knock/qq/sessions/qq-q/timeline.jsonl")]
    (if (.exists (java.io.File. timeline-file))
      (do
        (println "✅ Timeline file exists")
        (println "📏 Line count:" (count (str/split-lines (slurp timeline-file))))
        (println "📝 Latest entry:")
        (println (last (str/split-lines (slurp timeline-file)))))
      (println "❌ Timeline file does not exist")))
  
  ;; Step 6: Check for our test input
  (println "\n📊 Step 6: Search for test input in timeline")
  (let [timeline-file (str (System/getProperty "user.home") "/.knock/qq/sessions/qq-q/timeline.jsonl")]
    (if (.exists (java.io.File. timeline-file))
      (let [content (slurp timeline-file)]
        (if (str/includes? content "TEST WATCHER CAPTURE")
          (println "🎉 SUCCESS: Test input found in timeline!")
          (println "❌ FAILED: Test input not found in timeline")))
      (println "❌ Timeline file missing")))
  
  (println "\n🧪 Test complete!"))

;; Run the test
(test-watcher-capture)
