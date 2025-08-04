(ns terminal-debug
  (:require [etaoin.api :as e]))

(defn debug-terminal-initialization []
  "Debug what's happening with terminal initialization"
  (let [driver (e/chrome {:headless false :size [1200 800]})]
    (try
      (println "🔍 Debugging terminal initialization...")
      
      ;; Navigate to terminal
      (e/go driver "http://localhost:9090/web/terminal-simple.html")
      (println "✅ Navigated to terminal page")
      
      ;; Wait for page to load and scripts to execute
      (println "⏳ Waiting 5 seconds for page load and script execution...")
      (e/wait 5)
      
      ;; Check console logs first - this will show our detailed initialization logs
      (println "🔍 Checking console logs for initialization details...")
      (let [logs (e/get-logs driver)]
        (doseq [log logs]
          (println (str "📋 " (:level log) ": " (:message log)))))
      
      ;; Check basic elements
      (println "\n🔍 Checking basic page elements...")
      (let [container (e/query driver {:class "terminal-container"})]
        (println (str "Terminal container: " (if container "✅ Found" "❌ Not found"))))
      
      (let [terminal (e/query driver {:id "terminal"})]
        (println (str "Terminal element: " (if terminal "✅ Found" "❌ Not found"))))
      
      ;; Wait longer for xterm to initialize
      (println "\n⏳ Waiting 10 more seconds for xterm initialization...")
      (e/wait 10)
      
      ;; Check console logs again after waiting
      (println "🔍 Checking console logs after waiting...")
      (let [logs (e/get-logs driver)]
        (doseq [log logs]
          (when (or (.contains (:message log) "xterm")
                   (.contains (:message log) "Terminal")
                   (.contains (:message log) "✅")
                   (.contains (:message log) "❌"))
            (println (str "📋 " (:level log) ": " (:message log))))))
      
      ;; Now check for xterm elements more carefully
      (println "\n🔍 Checking for xterm elements...")
      (try
        (let [all-elements (e/js-execute driver "return Array.from(document.querySelectorAll('*')).map(el => el.className).filter(c => c.includes('xterm'))")]
          (println (str "Elements with 'xterm' in className: " all-elements)))
        (catch Exception e
          (println (str "Error checking xterm elements: " (.getMessage e)))))
      
      ;; Check if terminal has any content
      (println "\n🔍 Checking terminal content...")
      (try
        (let [terminal-html (e/js-execute driver "return document.querySelector('#terminal').innerHTML")]
          (println (str "Terminal HTML length: " (count terminal-html)))
          (if (> (count terminal-html) 100)
            (println (str "Terminal HTML preview: " (subs terminal-html 0 200) "..."))
            (println (str "Terminal HTML: " terminal-html))))
        (catch Exception e
          (println (str "Error checking terminal content: " (.getMessage e)))))
      
      ;; Check if xterm viewport exists now
      (try
        (let [viewport (e/query driver {:css ".xterm-viewport"})]
          (if viewport
            (do
              (println "✅ Xterm viewport found!")
              (let [scroll-info (e/js-execute driver "
                const vp = document.querySelector('.xterm-viewport');
                return {
                  scrollHeight: vp.scrollHeight,
                  clientHeight: vp.clientHeight,
                  scrollTop: vp.scrollTop,
                  hasScrollbar: vp.scrollHeight > vp.clientHeight
                };
              ")]
                (println (str "📊 Viewport info: " scroll-info))))
            (println "❌ Xterm viewport still not found")))
        (catch Exception e
          (println (str "Xterm viewport check failed: " (.getMessage e)))))
      
      (println "\n🎯 Debug completed - browser will stay open for manual inspection...")
      (e/wait 20)
      
      (catch Exception e
        (println (str "❌ Debug failed: " (.getMessage e))))
      
      (finally
        (e/quit driver)))))

(defn run-debug []
  "Run the terminal debug"
  (println "🚀 Running terminal initialization debug...")
  (debug-terminal-initialization)
  (println "🎯 Debug completed!"))
