(ns qq.browser.control
  "QQ Browser Automation with Etaoin - Debug and test web interfaces"
  (:require [etaoin.api :as e]
            [clojure.data.json :as json]
            [clojure.string :as str]))

;; Browser configuration
(def ^:private DASHBOARD-URL "http://localhost:9090/web/dashboard.html")
(def ^:private SCREENSHOT-DIR "screenshots")

;; Ensure screenshots directory exists
(defn- ensure-screenshot-dir []
  (let [dir (java.io.File. SCREENSHOT-DIR)]
    (when-not (.exists dir)
      (.mkdirs dir)
      (println (str "📁 Created screenshots directory: " SCREENSHOT-DIR)))))

;; Basic browser management
(defn start-browser
  "Start a Chrome browser instance"
  ([] (start-browser {:headless false}))
  ([opts]
   (println "🚀 Starting Chrome browser...")
   (let [driver (e/chrome (merge {:download-dir (str (System/getProperty "user.dir") "/downloads")
                                  :prefs {:profile.default_content_settings.popups 0}} 
                                 opts))]
     (println "✅ Chrome browser started")
     driver)))

(defn start-headless-browser
  "Start a headless Chrome browser for automated testing"
  []
  (println "🤖 Starting headless Chrome browser...")
  (start-browser {:headless true}))

;; QQ Dashboard specific functions
(defn navigate-to-dashboard
  "Navigate to QQ dashboard"
  [driver]
  (println (str "🌐 Navigating to: " DASHBOARD-URL))
  (e/go driver DASHBOARD-URL)
  (e/wait-visible driver "body" {:timeout 30})  ; Increased timeout
  (println "✅ Dashboard loaded"))

(defn take-screenshot
  "Take a screenshot with timestamp"
  ([driver] (take-screenshot driver "dashboard"))
  ([driver name]
   (ensure-screenshot-dir)
   (let [timestamp (.format (java.time.LocalDateTime/now) 
                           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss"))
         filename (str SCREENSHOT-DIR "/" name "_" timestamp ".png")]
     (e/screenshot driver filename)
     (println (str "📸 Screenshot saved: " filename))
     filename)))

(defn get-console-logs
  "Get browser console logs"
  [driver]
  (try
    (let [logs (e/get-logs driver :browser)]
      (println "📋 Console logs:")
      (doseq [log logs]
        (println (str "  " (:level log) ": " (:message log))))
      logs)
    (catch Exception e
      (println "⚠️ Could not retrieve console logs:" (.getMessage e))
      [])))

(defn check-element-exists
  "Check if element exists and print result"
  [driver selector description]
  (let [exists? (e/exists? driver selector)]
    (println (str (if exists? "✅" "❌") " " description ": " exists?))
    exists?))

(defn get-element-info
  "Get detailed information about an element"
  [driver selector]
  (when (e/exists? driver selector)
    (let [text (try (e/get-element-text driver selector) (catch Exception e ""))
          html (try (e/get-element-attr driver selector "innerHTML") (catch Exception e ""))
          classes (try (e/get-element-attr driver selector "class") (catch Exception e ""))]
      {:text text
       :html html
       :classes classes
       :exists true})))

;; QQ Dashboard debugging functions
(defn debug-dashboard-elements
  "Debug dashboard elements and their state"
  [driver]
  (println "🔍 Debugging dashboard elements...")
  
  ;; Check main elements
  (check-element-exists driver ".container" "Main container")
  (check-element-exists driver ".header" "Header")
  (check-element-exists driver ".sessions-section" "Sessions section")
  (check-element-exists driver ".sessions-grid" "Sessions grid")
  (check-element-exists driver ".session-card" "Session cards")
  (check-element-exists driver ".empty-state" "Empty state message")
  (check-element-exists driver ".system-info" "System info sidebar")
  
  ;; Get session count
  (when (e/exists? driver ".session-count")
    (let [count-text (e/get-element-text driver ".session-count")]
      (println (str "📊 Session count display: " count-text))))
  
  ;; Check sessions grid content
  (when (e/exists? driver ".sessions-grid")
    (let [grid-info (get-element-info driver ".sessions-grid")]
      (println (str "📋 Sessions grid HTML: " (subs (:html grid-info) 0 (min 200 (count (:html grid-info)))) "...")))))

(defn test-api-calls
  "Test API calls from browser JavaScript"
  [driver]
  (println "🔍 Testing API calls...")
  
  ;; Test sessions API
  (let [sessions-result (e/js-execute driver "
    return fetch('/api/sessions')
      .then(response => response.json())
      .then(data => {
        console.log('Sessions API response:', data);
        return data;
      })
      .catch(error => {
        console.error('Sessions API error:', error);
        return {error: error.message};
      });")]
    (println (str "📊 Sessions API result: " sessions-result)))
  
  ;; Test system status API
  (let [status-result (e/js-execute driver "
    return fetch('/api/system/status')
      .then(response => response.json())
      .then(data => {
        console.log('System status API response:', data);
        return data;
      })
      .catch(error => {
        console.error('System status API error:', error);
        return {error: error.message};
      });")]
    (println (str "⚡ System status API result: " status-result))))

(defn debug-javascript-state
  "Debug JavaScript application state"
  [driver]
  (println "🔍 Debugging JavaScript state...")
  
  ;; Check if dashboard object exists
  (let [dashboard-exists (e/js-execute driver "return typeof dashboard !== 'undefined';")]
    (println (str "🤖 Dashboard object exists: " dashboard-exists)))
  
  ;; Check if QQDashboard class exists
  (let [class-exists (e/js-execute driver "return typeof QQDashboard !== 'undefined';")]
    (println (str "📱 QQDashboard class exists: " class-exists)))
  
  ;; Get any JavaScript errors
  (e/js-execute driver "
    window.addEventListener('error', function(e) {
      console.error('JavaScript Error:', e.error);
    });
    
    // Check if dashboard initialization worked
    if (typeof dashboard !== 'undefined') {
      console.log('Dashboard initialized successfully');
    } else {
      console.error('Dashboard not initialized');
    }"))

;; Main debugging function
(defn debug-session-rendering-issue
  "Debug why sessions aren't rendering despite API returning data"
  []
  (println "🎯 DEBUGGING SESSION RENDERING ISSUE")
  (println "===================================")
  
  (let [driver (start-browser)]
    (try
      ;; Navigate to dashboard
      (navigate-to-dashboard driver)
      
      ;; Take initial screenshot
      (take-screenshot driver "initial-load")
      
      ;; Wait a moment for JavaScript to load
      (Thread/sleep 2000)
      
      ;; Debug elements
      (debug-dashboard-elements driver)
      
      ;; Test API calls
      (test-api-calls driver)
      
      ;; Debug JavaScript state
      (debug-javascript-state driver)
      
      ;; Get console logs
      (get-console-logs driver)
      
      ;; Take final screenshot
      (take-screenshot driver "after-debug")
      
      ;; Keep browser open for manual inspection
      (println "")
      (println "🔍 Browser is open for manual inspection")
      (println "📊 Check the developer console for more details")
      (println "⏸️  Press Enter to close browser...")
      (read-line)
      
      (catch Exception e
        (println (str "❌ Error during debugging: " (.getMessage e)))
        (take-screenshot driver "error-state"))
      (finally
        (println "🛑 Closing browser...")
        (e/quit driver)))))

;; Quick testing functions
(defn quick-screenshot
  "Take a quick screenshot of the dashboard"
  []
  (let [driver (start-headless-browser)]
    (try
      (navigate-to-dashboard driver)
      (Thread/sleep 3000) ; Wait for loading
      (take-screenshot driver "quick-check")
      (finally
        (e/quit driver)))))

(defn test-dashboard-loads
  "Quick test to verify dashboard loads without errors"
  []
  (println "🚀 Testing dashboard loads...")
  (let [driver (start-headless-browser)]
    (try
      (navigate-to-dashboard driver)
      (Thread/sleep 2000)
      
      (let [title (e/get-title driver)
            has-content (e/exists? driver ".container")]
        (println (str "📄 Page title: " title))
        (println (str "✅ Dashboard content loaded: " has-content))
        
        (if (and (str/includes? title "QQ Dashboard") has-content)
          (println "🎉 Dashboard loads successfully!")
          (println "❌ Dashboard loading issues detected")))
      
      (finally
        (e/quit driver)))))

;; Browser debugging functions for live inspection
(defn debug-live-browser
  "Debug the currently open browser (call this while browser is open)"
  [driver]
  (println "🔍 LIVE BROWSER DEBUGGING")
  (println "========================")
  
  ;; Check page title and URL
  (let [title (e/get-title driver)
        url (e/get-url driver)]
    (println (str "📄 Page title: " title))
    (println (str "🌐 Current URL: " url)))
  
  ;; Check if main elements exist
  (println "\n📋 Element Existence Check:")
  (check-element-exists driver ".container" "Main container")
  (check-element-exists driver ".sessions-grid" "Sessions grid")
  (check-element-exists driver ".session-card" "Session cards")
  (check-element-exists driver ".empty-state" "Empty state message")
  
  ;; Get sessions grid content
  (println "\n📊 Sessions Grid Analysis:")
  (if (e/exists? driver ".sessions-grid")
    (let [grid-html (e/get-element-attr driver ".sessions-grid" "innerHTML")]
      (println (str "Grid HTML length: " (count grid-html)))
      (println (str "Grid HTML preview: " (subs grid-html 0 (min 200 (count grid-html))) "...")))
    (println "❌ Sessions grid not found"))
  
  ;; Test API calls via JavaScript
  (println "\n🔍 Testing API Calls:")
  (let [sessions-api (e/js-execute driver "
    return fetch('/api/sessions')
      .then(r => r.json())
      .then(data => JSON.stringify(data))
      .catch(e => 'ERROR: ' + e.message);")]
    (println (str "📊 Sessions API result: " sessions-api)))
  
  ;; Check JavaScript state
  (println "\n🤖 JavaScript State Check:")
  (let [dashboard-exists (e/js-execute driver "return typeof dashboard !== 'undefined';")
        dashboard-class-exists (e/js-execute driver "return typeof QQDashboard !== 'undefined';")]
    (println (str "Dashboard object exists: " dashboard-exists))
    (println (str "QQDashboard class exists: " dashboard-class-exists)))
  
  ;; Get console errors
  (println "\n📋 Console Logs:")
  (try
    (let [logs (e/get-logs driver :browser)]
      (if (empty? logs)
        (println "✅ No console logs found")
        (doseq [log (take 10 logs)]
          (println (str "  " (:level log) ": " (:message log))))))
    (catch Exception e
      (println "⚠️ Could not retrieve console logs")))
  
  ;; Check what's actually in the sessions grid
  (println "\n🔍 Sessions Grid Deep Inspection:")
  (let [grid-children (e/js-execute driver "
    const grid = document.querySelector('.sessions-grid');
    if (grid) {
      return {
        childCount: grid.children.length,
        innerHTML: grid.innerHTML.substring(0, 500),
        classes: grid.className
      };
    }
    return {error: 'Grid not found'};")]
    (println (str "Grid inspection: " grid-children))))

(defn extract-browser-debug-info
  "Extract comprehensive debug info from browser (run this while browser is open)"
  [driver]
  (println "📊 EXTRACTING COMPREHENSIVE DEBUG INFO")
  (println "======================================")
  
  ;; Get all the debug info
  (debug-live-browser driver)
  
  ;; Additional deep inspection
  (println "\n🔬 Deep JavaScript Inspection:")
  (let [js-debug-info (e/js-execute driver "
    // Comprehensive debug info
    const debugInfo = {
      // Page state
      readyState: document.readyState,
      title: document.title,
      
      // Dashboard elements
      hasContainer: !!document.querySelector('.container'),
      hasSessionsGrid: !!document.querySelector('.sessions-grid'),
      hasSessionCards: document.querySelectorAll('.session-card').length,
      hasEmptyState: !!document.querySelector('.empty-state'),
      
      // JavaScript state
      dashboardExists: typeof dashboard !== 'undefined',
      dashboardClassExists: typeof QQDashboard !== 'undefined',
      
      // API test
      apiTest: 'Will test separately',
      
      // Sessions grid content
      sessionsGridHTML: document.querySelector('.sessions-grid') ? 
        document.querySelector('.sessions-grid').innerHTML.substring(0, 300) : 'NOT FOUND'
    };
    
    return JSON.stringify(debugInfo, null, 2);")]
    (println js-debug-info)))
(defn interactive-debug
  "Start interactive debugging session"
  []
  (println "🎮 INTERACTIVE DEBUGGING SESSION")
  (println "===============================")
  (println "🌐 Starting browser for interactive debugging...")
  
  (let [driver (start-browser)]
    (navigate-to-dashboard driver)
    (println "")
    (println "🔍 Browser is ready for debugging!")
    (println "📊 Dashboard loaded at: " DASHBOARD-URL)
    (println "🛠️  Available commands:")
    (println "  - Check developer console")
    (println "  - Inspect elements")
    (println "  - Test API calls manually")
    (println "")
    (println "⏸️  Press Enter when done to close browser...")
    (read-line)
    (e/quit driver)
    (println "✅ Interactive debugging session ended")))

(defn simple-browser-test
  "Simple browser test - just open dashboard and wait"
  []
  (println "🌐 SIMPLE BROWSER TEST")
  (println "=====================")
  (println "🚀 Opening Chrome browser to QQ Dashboard...")
  
  (let [driver (start-browser)]
    (try
      (println "🌐 Navigating to dashboard...")
      (e/go driver DASHBOARD-URL)
      (Thread/sleep 3000) ; Wait for page load
      
      (println "✅ Browser opened successfully!")
      (println "📊 Dashboard URL: " DASHBOARD-URL)
      (println "")
      (println "🔍 WHAT TO CHECK:")
      (println "  1. Does the dashboard load visually?")
      (println "  2. Open Developer Console (F12)")
      (println "  3. Check for JavaScript errors")
      (println "  4. Look at Network tab for API calls")
      (println "  5. Inspect the sessions grid element")
      (println "")
      (println "⏸️  Press Enter in this terminal to close browser...")
      (read-line)
      
      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (Thread/sleep 5000))
      (finally
        (println "🛑 Closing browser...")
        (e/quit driver)
        (println "✅ Browser closed")))))

(defn test-create-session-button
  "Test the Create Session button functionality"
  [driver]
  (println "🧪 TESTING CREATE SESSION BUTTON")
  (println "================================")
  
  ;; First, refresh the page to get latest JavaScript
  (println "🔄 Refreshing page to load updated JavaScript...")
  (e/refresh driver)
  (Thread/sleep 3000)
  
  ;; Check if button exists
  (if (e/exists? driver ".btn-success")
    (do
      (println "✅ Create Session button found")
      
      ;; Take screenshot before clicking
      (take-screenshot driver "before-create-session")
      
      ;; Click the Create Session button
      (println "🖱️  Clicking Create Session button...")
      (e/click driver ".btn-success")
      (Thread/sleep 1000)
      
      ;; Handle the prompt (if it appears)
      (try
        (println "📝 Handling session name prompt...")
        (e/js-execute driver "
          // Override prompt to automatically provide a test name
          window.prompt = function(message) {
            console.log('Prompt intercepted:', message);
            return 'etaoin-test-session';
          };
        ")
        
        ;; Click button again now that prompt is overridden
        (e/click driver ".btn-success")
        (Thread/sleep 2000)
        
        ;; Check console logs
        (println "📋 Checking console logs...")
        (let [logs (e/get-logs driver :browser)]
          (doseq [log (take 10 logs)]
            (println (str "  Console: " (:level log) " - " (:message log)))))
        
        ;; Take screenshot after
        (take-screenshot driver "after-create-session")
        
        ;; Check if sessions updated
        (println "🔍 Checking if sessions updated...")
        (let [sessions-result (e/js-execute driver "
          return fetch('/api/sessions')
            .then(r => r.json())
            .then(data => data.length)
            .catch(e => 'ERROR');")]
          (println (str "📊 Number of sessions: " sessions-result)))
        
        (catch Exception e
          (println (str "⚠️ Error during test: " (.getMessage e)))))
    
    (println "❌ Create Session button not found")))

(defn automated-dashboard-test
  "Run automated tests on the dashboard"
  [driver]
  (println "🤖 AUTOMATED DASHBOARD TESTING")
  (println "==============================")
  
  ;; Test 1: Page load
  (println "\n1️⃣ Testing page load...")
  (e/go driver DASHBOARD-URL)
  (Thread/sleep 3000)
  (println "✅ Page loaded")
  
  ;; Test 2: API calls
  (println "\n2️⃣ Testing API calls...")
  (let [api-result (e/js-execute driver "
    return Promise.all([
      fetch('/api/sessions').then(r => r.json()),
      fetch('/api/system/status').then(r => r.json())
    ]).then(results => ({
      sessions: results[0].length,
      status: results[1].cpu
    })).catch(e => ({error: e.message}));")]
    (println (str "📊 API test result: " api-result)))
  
  ;; Test 3: Create Session button
  (println "\n3️⃣ Testing Create Session button...")
  (test-create-session-button driver)
  
  ;; Test 4: Final state
  (println "\n4️⃣ Final dashboard state...")
  (debug-live-browser driver)
  
  (println "\n✅ Automated testing complete!"))

(defn interactive-automated-test
  "Run interactive automated test - best of both worlds"
  []
  (println "🎮 INTERACTIVE AUTOMATED TESTING")
  (println "================================")
  (println "🤖 I'll control the browser automatically")
  (println "👀 You can watch the automation happen")
  (println "")
  
  (let [driver (start-browser)]
    (try
      ;; Run automated tests
      (automated-dashboard-test driver)
      
      ;; Keep browser open for inspection
      (println "")
      (println "🔍 Browser will stay open for manual inspection...")
      (println "📊 Check the dashboard state and console logs")
      (println "⏸️  Press Enter to close browser...")
      (read-line)
      
      (catch Exception e
        (println (str "❌ Error during automated testing: " (.getMessage e)))
        (take-screenshot driver "error-automated-test"))
      (finally
        (println "🛑 Closing browser...")
        (e/quit driver)
        (println "✅ Automated testing session ended")))))
  "Debug the current browser - to be run in a separate terminal while browser is open"
  []
  (println "🔍 DEBUGGING CURRENT BROWSER SESSION")
  (println "===================================")
  (println "⚠️  This function connects to an existing browser session")
  (println "🌐 Make sure the browser is open from 'bb browser-simple'")
  (println "")
  
  ;; This is a simplified approach - we'll create a new driver connection
  (let [driver (start-browser)]
    (try
      (e/go driver DASHBOARD-URL)
      (Thread/sleep 2000)
      (extract-browser-debug-info driver)
      (finally
        (e/quit driver)))))
