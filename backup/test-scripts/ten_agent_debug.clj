(ns qq.browser.ten-agent-debug
  "Enhanced TEN Agent debugging with graph detection and console inspection"
  (:require [etaoin.api :as e]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

;; TEN Agent URLs
(def PLAYGROUND-URL "http://localhost:3000")
(def TMAN-DESIGNER-URL "http://localhost:49483")

(defn analyze-console-logs
  "Analyze browser console logs for TEN Agent issues"
  [driver]
  (println "🔍 Analyzing browser console logs...")
  (let [logs (e/get-logs driver :browser)]
    (println (str "📋 Total console entries: " (count logs)))
    
    ;; Categorize logs
    (let [errors (filter #(= "SEVERE" (:level %)) logs)
          warnings (filter #(= "WARNING" (:level %)) logs)
          info (filter #(= "INFO" (:level %)) logs)]
      
      (println (str "❌ Errors: " (count errors)))
      (println (str "⚠️  Warnings: " (count warnings)))
      (println (str "ℹ️  Info: " (count info)))
      
      ;; Show errors in detail
      (when (seq errors)
        (println "\n🚨 ERROR DETAILS:")
        (doseq [error errors]
          (println (str "  ❌ " (:message error)))))
      
      ;; Look for specific TEN Agent related logs
      (println "\n🎯 TEN Agent Related Logs:")
      (doseq [log logs]
        (let [msg (str/lower-case (:message log))]
          (when (or (str/includes? msg "ten")
                    (str/includes? msg "agent")
                    (str/includes? msg "deepgram")
                    (str/includes? msg "graph")
                    (str/includes? msg "websocket")
                    (str/includes? msg "api")
                    (str/includes? msg "connect"))
            (println (str "  " (:level log) ": " (:message log))))))
      
      ;; Return summary
      {:total (count logs)
       :errors (count errors)
       :warnings (count warnings)
       :error-messages (map :message errors)})))

(defn check-graph-options
  "Check for available graph options in the playground"
  [driver]
  (println "🎯 Checking for graph options...")
  
  ;; Wait a bit for the page to fully load
  (Thread/sleep 5000)
  
  ;; Look for the Select Graph dropdown
  (if (e/exists? driver "button:contains('Select Graph')")
    (do
      (println "✅ Found 'Select Graph' dropdown")
      
      ;; Click to open dropdown
      (e/click driver "button:contains('Select Graph')")
      (Thread/sleep 2000)
      
      ;; Look for dropdown options
      (let [options (e/query-all driver "[role='option'], .option, li")]
        (println (str "📋 Found " (count options) " dropdown options"))
        
        ;; Try to get text from options
        (doseq [i (range (min 10 (count options)))]
          (try
            (let [option (nth options i)
                  text (e/get-element-text driver option)]
              (when (not (str/blank? text))
                (println (str "  📄 Option " (inc i) ": " text))
                ;; Check if this is our voice-assistant-star
                (when (str/includes? (str/lower-case text) "voice")
                  (println "    🎤 ⭐ FOUND VOICE ASSISTANT OPTION!"))))
            (catch Exception e
              (println (str "    ⚠️ Could not read option " (inc i))))))
        
        ;; Take screenshot of dropdown
        (e/screenshot driver "screenshots/ten-agent-graph-dropdown-open.png")
        
        ;; Close dropdown by clicking elsewhere
        (e/click driver "body")
        options)
      
      (do
        (println "❌ 'Select Graph' dropdown not found")
        ;; Look for any other graph-related elements
        (let [graph-elements (e/query-all driver "*[class*='graph'], *[id*='graph'], *[data-testid*='graph']")]
          (println (str "🔍 Found " (count graph-elements) " graph-related elements"))
          graph-elements)))))

(defn check-api-connectivity
  "Check API connectivity and backend status"
  [driver]
  (println "🌐 Checking API connectivity...")
  
  ;; Test various API endpoints
  (let [api-tests [
        {:name "Graphs API" :url "/api/designer/v1/graphs"}
        {:name "Apps API" :url "/api/designer/v1/apps"}
        {:name "Health Check" :url "/api/health"}
        {:name "Status" :url "/api/status"}]]
    
    (doseq [test api-tests]
      (try
        (let [result (e/js-execute driver 
                       (str "return fetch('" (:url test) "')
                             .then(r => ({status: r.status, ok: r.ok, statusText: r.statusText}))
                             .catch(e => ({error: e.message}))"))]
          (println (str "  " (:name test) ": " result)))
        (catch Exception e
          (println (str "  " (:name test) ": Failed - " (.getMessage e))))))))

(defn check-websocket-connections
  "Check for WebSocket connections"
  [driver]
  (println "🔌 Checking WebSocket connections...")
  
  (let [ws-info (e/js-execute driver 
                  "return {
                     webSocketSupport: typeof WebSocket !== 'undefined',
                     activeConnections: window.webSocketConnections || 'none detected',
                     connectionState: window.connectionState || 'unknown'
                   }")]
    (println (str "  WebSocket Support: " (:webSocketSupport ws-info)))
    (println (str "  Active Connections: " (:activeConnections ws-info)))
    (println (str "  Connection State: " (:connectionState ws-info)))))

(defn comprehensive-ten-agent-debug
  "Comprehensive debugging of TEN Agent playground"
  []
  (println "🚀 Starting comprehensive TEN Agent debugging...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Step 1: Navigate and wait
      (println "\n📍 Step 1: Navigating to TEN Agent Playground...")
      (e/go driver PLAYGROUND-URL)
      (Thread/sleep 3000)  ; Give it time to start loading
      
      ;; Step 2: Take initial screenshot
      (e/screenshot driver "screenshots/ten-agent-debug-1-initial.png")
      (println "📸 Initial screenshot taken")
      
      ;; Step 3: Check page loading state
      (println "\n📍 Step 2: Checking page loading state...")
      (let [ready-state (e/js-execute driver "return document.readyState")
            title (e/get-title driver)
            url (e/js-execute driver "return window.location.href")]
        (println (str "  Document Ready State: " ready-state))
        (println (str "  Page Title: " title))
        (println (str "  Current URL: " url)))
      
      ;; Step 4: Analyze console logs
      (println "\n📍 Step 3: Analyzing console logs...")
      (let [log-summary (analyze-console-logs driver)]
        (when (> (:errors log-summary) 0)
          (println "🚨 ERRORS DETECTED - This may explain loading issues!")))
      
      ;; Step 5: Check for basic elements
      (println "\n📍 Step 4: Checking for basic page elements...")
      (let [has-body (e/exists? driver "body")
            has-header (e/exists? driver "header")
            has-h1 (e/exists? driver "h1")
            has-buttons (count (e/query-all driver "button"))
            body-text (try (e/get-element-text driver "body") (catch Exception e "Could not read body"))]
        (println (str "  Body element: " has-body))
        (println (str "  Header element: " has-header))
        (println (str "  H1 element: " has-h1))
        (println (str "  Button count: " has-buttons))
        (println (str "  Body text length: " (count body-text)))
        
        ;; If we have content, look for TEN Agent specific elements
        (when (> (count body-text) 100)
          (println "✅ Page has content, looking for TEN Agent elements...")
          (let [ten-text (str/includes? (str/lower-case body-text) "ten agent")
                connect-btn (e/exists? driver "button:contains('Connect')")
                graph-dropdown (e/exists? driver "button:contains('Select Graph')")]
            (println (str "  Contains 'TEN Agent': " ten-text))
            (println (str "  Connect button: " connect-btn))
            (println (str "  Graph dropdown: " graph-dropdown)))))
      
      ;; Step 6: Check graph options if available
      (println "\n📍 Step 5: Checking graph options...")
      (check-graph-options driver)
      
      ;; Step 7: Check API connectivity
      (println "\n📍 Step 6: Checking API connectivity...")
      (check-api-connectivity driver)
      
      ;; Step 8: Check WebSocket connections
      (println "\n📍 Step 7: Checking WebSocket connections...")
      (check-websocket-connections driver)
      
      ;; Step 9: Final screenshot
      (e/screenshot driver "screenshots/ten-agent-debug-2-final.png")
      (println "\n📸 Final screenshot taken")
      
      (println "\n✅ Comprehensive TEN Agent debugging completed!")
      (println "📁 Check screenshots/ directory for visual debugging")
      
      (catch Exception e
        (println (str "\n❌ Debug failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/ten-agent-debug-error.png"))
      
      (finally
        (println "\n🔄 Keeping browser open for 30 seconds for manual inspection...")
        (Thread/sleep 30000)  ; Keep browser open for manual inspection
        (e/quit driver)))))

(defn quick-console-check
  "Quick check of console logs without full browser automation"
  []
  (println "⚡ Quick console check...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (e/go driver PLAYGROUND-URL)
      (Thread/sleep 10000)  ; Wait 10 seconds for loading
      
      (println "\n📋 Console Logs After 10 seconds:")
      (analyze-console-logs driver)
      
      (println "\n🔄 Keeping browser open for manual inspection...")
      (Thread/sleep 60000)  ; Keep open for 1 minute
      
      (catch Exception e
        (println (str "❌ Quick check failed: " (.getMessage e))))
      
      (finally
        (e/quit driver)))))

;; Main execution
(defn -main [& args]
  (case (first args)
    "full" (comprehensive-ten-agent-debug)
    "console" (quick-console-check)
    (do
      (println "Usage: bb ten-agent-debug [full|console]")
      (println "  full    - Comprehensive debugging with graph detection")
      (println "  console - Quick console log check"))))
