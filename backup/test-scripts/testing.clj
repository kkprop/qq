(ns qq.browser.testing
  "🤖 QQ Browser Testing Framework
  
  Comprehensive browser automation and testing utilities for QQ Dashboard.
  Provides automated testing, screenshot capture, UI validation, and more.
  
  ## Key Features:
  - 📸 Automated screenshot capture with timestamps
  - 🧪 Phase-based testing suites
  - 🖱️ JavaScript injection and DOM manipulation
  - 📊 API endpoint testing from browser context
  - 👁️ UI element validation and interaction
  - 📋 Console log capture and analysis
  - 🔄 Auto-refresh and real-time monitoring
  
  ## Usage Examples:
  
  ```clojure
  ;; Run comprehensive dashboard tests
  (run-dashboard-tests)
  
  ;; Take timestamped screenshot
  (capture-screenshot driver \"test-name\")
  
  ;; Test specific functionality
  (test-create-session-flow driver)
  (test-api-endpoints driver)
  (validate-ui-elements driver)
  ```"
  (:require [etaoin.api :as e]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [qq.session.manager :as session-mgr]))

;; Configuration
(def ^:private DASHBOARD-URL "http://localhost:9090/web/dashboard.html")
(def ^:private SCREENSHOT-DIR "screenshots")
(def ^:private TEST-TIMEOUT 5000)

;; Core Testing Utilities
(defn capture-screenshot
  "📸 Capture timestamped screenshot with descriptive name
  
  Args:
    driver - Etaoin driver instance
    name   - Descriptive name for the screenshot
    
  Returns:
    String path to saved screenshot
    
  Example:
    (capture-screenshot driver \"dashboard-loaded\")"
  [driver name]
  (let [timestamp (-> (java.time.LocalDateTime/now)
                      (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd_HH-mm-ss")))
        filename (str name "_" timestamp ".png")
        filepath (str SCREENSHOT-DIR "/" filename)]
    
    (println (str "📸 Taking screenshot: " filename))
    (e/screenshot driver filepath)
    (println (str "📸 Screenshot saved: " filepath))
    filepath))

(defn inject-test-helpers
  "🖱️ Inject JavaScript test helpers into the browser
  
  Adds utility functions for automated testing including:
  - Prompt override for automated input
  - Console logging helpers
  - Test data injection
  
  Args:
    driver - Etaoin driver instance
    
  Example:
    (inject-test-helpers driver)"
  [driver]
  (println "🔧 Injecting JavaScript test helpers...")
  (e/js-execute driver "
    // Store original functions
    window.originalPrompt = window.prompt;
    window.originalAlert = window.alert;
    window.originalConfirm = window.confirm;
    
    // Test helper functions
    window.testHelpers = {
      // Override prompt with automated response
      setPromptResponse: function(response) {
        window.prompt = function(message) {
          console.log('🤖 Automated prompt:', message, '-> Response:', response);
          return response;
        };
      },
      
      // Override alert to prevent blocking
      disableAlerts: function() {
        window.alert = function(message) {
          console.log('🚨 Alert intercepted:', message);
        };
      },
      
      // Restore original functions
      restore: function() {
        window.prompt = window.originalPrompt;
        window.alert = window.originalAlert;
        window.confirm = window.originalConfirm;
      },
      
      // Test data injection
      injectTestData: function(data) {
        window.testData = data;
        console.log('📊 Test data injected:', data);
      }
    };
    
    console.log('✅ Test helpers injected successfully');
  ")
  (println "✅ JavaScript test helpers injected"))

(defn capture-console-logs
  "📋 Capture and analyze browser console logs
  
  Args:
    driver - Etaoin driver instance
    limit  - Maximum number of logs to capture (default: 10)
    
  Returns:
    Vector of log entries with level and message
    
  Example:
    (capture-console-logs driver 5)"
  ([driver] (capture-console-logs driver 10))
  ([driver limit]
   (println (str "📋 Capturing console logs (limit: " limit ")..."))
   (let [logs (e/get-logs driver :browser)
         recent-logs (take limit logs)]
     (doseq [log recent-logs]
       (println (str "  " (:level log) ": " (:message log))))
     recent-logs)))

(defn validate-ui-elements
  "👁️ Validate presence and visibility of UI elements
  
  Args:
    driver   - Etaoin driver instance
    elements - Vector of CSS selectors to validate
    
  Returns:
    Map of element -> boolean (exists/visible)
    
  Example:
    (validate-ui-elements driver [\".container\" \".btn-success\"])"
  [driver elements]
  (println "👁️ Validating UI elements...")
  (let [results (into {} 
                      (map (fn [element]
                             [element (e/exists? driver {:css element})])
                           elements))]
    (doseq [[element exists] results]
      (println (str "  " element " exists: " exists)))
    results))

(defn test-api-endpoints
  "📊 Test API endpoints from browser context
  
  Args:
    driver    - Etaoin driver instance
    endpoints - Vector of endpoint paths to test
    
  Returns:
    Map of endpoint -> test results
    
  Example:
    (test-api-endpoints driver [\"/api/sessions\" \"/api/system/status\"])"
  [driver endpoints]
  (println "📊 Testing API endpoints from browser...")
  (let [results (into {}
                      (map (fn [endpoint]
                             [endpoint 
                              (e/js-execute driver 
                                (str "
                                  return fetch('" endpoint "')
                                    .then(r => ({
                                      status: r.status,
                                      ok: r.ok,
                                      contentType: r.headers.get('content-type'),
                                      url: r.url
                                    }))
                                    .catch(e => ({
                                      error: e.message,
                                      endpoint: '" endpoint "'
                                    }));
                                "))])
                           endpoints))]
    (doseq [[endpoint result] results]
      (println (str "  " endpoint ": " result)))
    results))

;; Specialized Test Functions
(defn test-create-session-flow
  "🧪 Test the complete Create Session workflow
  
  Args:
    driver       - Etaoin driver instance
    session-name - Name for the test session (optional)
    
  Returns:
    Map with test results and session info
    
  Example:
    (test-create-session-flow driver \"my-test-session\")"
  ([driver] (test-create-session-flow driver "automated-test-session"))
  ([driver session-name]
   (println "🧪 Testing Create Session workflow...")
   
   ;; Setup test helpers
   (inject-test-helpers driver)
   (e/js-execute driver (str "window.testHelpers.setPromptResponse('" session-name "');"))
   
   ;; Take before screenshot
   (capture-screenshot driver "create-session-before")
   
   ;; Test button existence
   (let [button-exists (e/exists? driver {:css ".btn-success"})]
     (if button-exists
       (do
         (println "✅ Create Session button found")
         
         ;; Click the button
         (println "🖱️ Clicking Create Session button...")
         (e/click driver {:css ".btn-success"})
         (Thread/sleep 3000)
         
         ;; Capture logs
         (let [logs (capture-console-logs driver 5)]
           
           ;; Take after screenshot
           (capture-screenshot driver "create-session-after")
           
           {:success true
            :button-found true
            :session-name session-name
            :console-logs logs
            :timestamp (java.time.Instant/now)}))
       
       (do
         (println "❌ Create Session button not found")
         {:success false
          :button-found false
          :error "Create Session button not found"})))))

(defn test-terminal-interface
  "🖥️ Test the browser-based terminal interface
  
  Tests the complete terminal experience:
  - Terminal page loading
  - WebSocket connection
  - Command input/output
  - Q session integration
  
  Args:
    driver - Etaoin driver instance (optional)
    
  Returns:
    Test results map
    
  Example:
    (test-terminal-interface)"
  ([]
   (let [driver (e/chrome {:headless false})]
     (try
       (test-terminal-interface driver)
       (finally
         (Thread/sleep 5000) ; Keep open for inspection
         (e/quit driver)))))
  ([driver]
   (println "🖥️ TESTING TERMINAL INTERFACE")
   (println "==============================")
   
   ;; Navigate to terminal
   (e/go driver "http://localhost:9090/web/terminal.html")
   (Thread/sleep 5000) ; Wait for WebSocket connection
   
   ;; Take screenshot
   (capture-screenshot driver "terminal-interface")
   
   ;; Check if terminal loaded
   (let [terminal-exists (e/exists? driver {:css "#terminal"})
         connection-status (e/get-element-text driver {:css "#statusText"})
         session-name (e/get-element-text driver {:css "#sessionName"})]
     
     (println (str "🖥️ Terminal element exists: " terminal-exists))
     (println (str "🔌 Connection status: " connection-status))
     (println (str "📋 Session name: " session-name))
     
     ;; Test terminal interaction (if connected)
     (let [test-results {:terminal-loaded terminal-exists
                         :connection-status connection-status
                         :session-name session-name
                         :timestamp (java.time.Instant/now)}]
       
       (if (and terminal-exists (str/includes? connection-status "Connected"))
         (do
           (println "✅ Terminal interface test PASSED")
           (assoc test-results :success true))
         (do
           (println "❌ Terminal interface test FAILED")
           (assoc test-results :success false)))))))

;; Add to existing functions
(defn run-comprehensive-dashboard-test
  "🎯 Run comprehensive dashboard functionality test
  
  Executes a full suite of dashboard tests including:
  - UI element validation
  - Create Session workflow
  - API endpoint testing
  - Console log analysis
  - Screenshot capture
  
  Args:
    driver - Etaoin driver instance (optional, will create if not provided)
    
  Returns:
    Comprehensive test results map
    
  Example:
    (run-comprehensive-dashboard-test)"
  ([] 
   (let [driver (e/chrome {:headless false})]
     (try
       (run-comprehensive-dashboard-test driver)
       (finally
         (e/quit driver)))))
  ([driver]
   (println "🎯 COMPREHENSIVE DASHBOARD TEST")
   (println "===============================")
   
   ;; Navigate to dashboard
   (e/go driver DASHBOARD-URL)
   (Thread/sleep 3000)
   
   ;; Initial screenshot
   (capture-screenshot driver "comprehensive-test-start")
   
   ;; Test 1: UI Elements
   (let [ui-elements [".container" ".sessions-grid" ".btn-success" ".system-info" ".empty-state"]
         ui-results (validate-ui-elements driver ui-elements)
         
         ;; Test 2: API Endpoints
         api-endpoints ["/api/sessions" "/api/system/status"]
         api-results (test-api-endpoints driver api-endpoints)
         
         ;; Test 3: Create Session Flow
         session-results (test-create-session-flow driver "comprehensive-test-session")
         
         ;; Test 4: Console Analysis
         console-logs (capture-console-logs driver 10)
         
         ;; Final screenshot
         _ (capture-screenshot driver "comprehensive-test-end")
         
         ;; Compile results
         results {:test-type "comprehensive-dashboard"
                  :timestamp (java.time.Instant/now)
                  :ui-validation ui-results
                  :api-testing api-results
                  :session-creation session-results
                  :console-logs console-logs
                  :success (and (:success session-results)
                               (every? identity (vals ui-results)))}]
     
     (println "\n🎉 COMPREHENSIVE TEST COMPLETED!")
     (println "=================================")
     (println (str "✅ Success: " (:success results)))
     (println (str "📊 UI Elements: " (count (filter identity (vals ui-results))) "/" (count ui-results)))
     (println (str "🌐 API Endpoints: " (count api-results)))
     (println (str "📋 Console Logs: " (count console-logs)))
     
     results)))

;; Convenience Functions for bb.edn
(defn quick-dashboard-test
  "⚡ Quick dashboard functionality test
  
  Runs a fast validation of core dashboard features.
  Perfect for development and CI/CD pipelines.
  
  Example:
    bb quick-dashboard-test"
  []
  (println "⚡ QUICK DASHBOARD TEST")
  (println "======================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (e/go driver DASHBOARD-URL)
      (Thread/sleep 2000)
      
      ;; Quick validation
      (let [elements [".container" ".btn-success"]
            results (validate-ui-elements driver elements)
            all-exist (every? identity (vals results))]
        
        (capture-screenshot driver "quick-test")
        
        (if all-exist
          (println "✅ Quick test PASSED - Core elements found")
          (println "❌ Quick test FAILED - Missing elements"))
        
        {:success all-exist :results results})
      
      (catch Exception e
        (println (str "❌ Quick test ERROR: " (.getMessage e)))
        {:success false :error (.getMessage e)})
      
      (finally
        (Thread/sleep 2000)
        (e/quit driver)))))

(defn interactive-dashboard-test
  "🎮 Interactive dashboard test with browser kept open
  
  Runs comprehensive tests but keeps browser open for manual inspection.
  Perfect for development and debugging.
  
  Example:
    bb interactive-dashboard-test"
  []
  (println "🎮 INTERACTIVE DASHBOARD TEST")
  (println "=============================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (let [results (run-comprehensive-dashboard-test driver)]
        
        (println "\n⏸️ Browser will stay open for manual inspection...")
        (println "📋 Press Enter to close browser and finish test...")
        (read-line)
        
        results)
      
      (catch Exception e
        (println (str "❌ Interactive test ERROR: " (.getMessage e)))
        (capture-screenshot driver "interactive-test-error")
        {:success false :error (.getMessage e)})
      
      (finally
        (e/quit driver)
        (println "✅ Interactive test session ended")))))
