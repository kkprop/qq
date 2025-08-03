(ns qq.process.monitor
  "🔍 Universal Process Monitor - Direct Output Streaming
  
  This monitors any process by PID and streams output without
  interfering with terminal input/output."
  (:require [clojure.core.async :as async]
            [babashka.process :as p]
            [clojure.java.io :as io])
  (:import [java.io BufferedReader InputStreamReader]
           [java.lang ProcessBuilder]))

(defn monitor-process-by-pid
  "Monitor a process by PID and stream output to channel
  
  This uses a separate monitoring approach that doesn't interfere
  with the original process terminal I/O."
  [pid output-chan]
  (async/go
    (try
      (println (str "🔍 Starting process monitor for PID: " pid))
      
      ;; Use lsof to find the process output file
      (let [lsof-result (p/process ["lsof" "-p" (str pid)] {:out :string})
            lsof-output (:out @lsof-result)]
        
        (println "📋 Process file descriptors:")
        (println lsof-output)
        
        ;; For now, we'll use a different approach - monitor via process tree
        ;; This avoids the TTY interference issue
        (loop [counter 0]
          (when (< counter 100) ; Limit for testing
            (let [ps-result (p/process ["ps" "-f" "-p" (str pid)] {:out :string})
                  ps-output (:out @ps-result)]
              
              (async/>! output-chan {:type "process-info"
                                     :pid pid
                                     :timestamp (System/currentTimeMillis)
                                     :content ps-output})
              
              (Thread/sleep 1000) ; Check every second
              (recur (inc counter))))))
      
      (catch Exception e
        (println (str "❌ Error monitoring process " pid ": " (.getMessage e)))
        (async/>! output-chan {:type "error"
                               :pid pid
                               :error (.getMessage e)}))
      
      (finally
        (println (str "🛑 Process monitor stopped for PID: " pid))
        (async/close! output-chan)))))

(defn create-process-stream
  "Create a streaming channel for process output"
  [pid]
  (let [output-chan (async/chan 100)]
    (monitor-process-by-pid pid output-chan)
    output-chan))

(defn monitor-current-chat
  "Monitor our current chat process (PID 68923)"
  []
  (let [chat-pid 68923
        stream-chan (create-process-stream chat-pid)]
    
    (println "🎯 Monitoring current chat process...")
    (println "📋 This will stream process info without interfering with terminal")
    
    ;; Return the channel for further processing
    stream-chan))

(defn test-process-monitor-stdout
  "Test capturing actual stdout text from our chat process"
  []
  (println "🧪 CAPTURING ACTUAL STDOUT TEXT")
  (println "================================")
  (println "")
  (println "📋 This will capture the real stdout text from our chat process...")
  (println "📋 PID 68923 - the actual text output, not just process info")
  (println "")
  
  ;; Try different approaches to capture stdout
  (println "🔍 Method 1: Using strace to monitor system calls...")
  (try
    (let [strace-result (p/process ["strace" "-p" "68923" "-e" "write"] 
                                   {:out :string :err :string :timeout 5000})]
      (println "📥 Strace output:")
      (println (:out @strace-result))
      (println (:err @strace-result)))
    (catch Exception e
      (println (str "❌ Strace failed: " (.getMessage e)))))
  
  (println "")
  (println "🔍 Method 2: Check process file descriptors...")
  (try
    (let [lsof-result (p/process ["lsof" "-p" "68923"] {:out :string})
          lsof-output (:out @lsof-result)]
      (println "📥 File descriptors:")
      (println lsof-output)
      
      ;; Look for stdout (fd 1)
      (when (re-find #"1u.*CHR.*ttys" lsof-output)
        (println "✅ Found stdout connected to terminal")))
    (catch Exception e
      (println (str "❌ lsof failed: " (.getMessage e)))))
  
  (println "")
  (println "🔍 Method 3: Try to read from /proc (Linux) or equivalent...")
  (try
    ;; On macOS, we might need different approach
    (let [proc-fd-path (str "/proc/" 68923 "/fd/1")]
      (if (.exists (java.io.File. proc-fd-path))
        (do
          (println (str "📥 Reading from " proc-fd-path))
          (println (slurp proc-fd-path)))
        (println "❌ /proc not available (macOS doesn't have /proc/PID/fd)")))
    (catch Exception e
      (println (str "❌ /proc method failed: " (.getMessage e)))))
  
  (println "")
  (println "🎯 CONCLUSION: We need a different approach for macOS stdout capture")
  (println "📋 The challenge: macOS doesn't expose process stdout like Linux /proc"))
