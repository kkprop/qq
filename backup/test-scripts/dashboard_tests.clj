(ns qq.browser.dashboard-tests
  "Comprehensive Dashboard Functionality Testing Suite"
  (:require [etaoin.api :as e]
            [clojure.data.json :as json]
            [qq.session.manager :as session-mgr]))

;; Test configuration
(def DASHBOARD-URL "http://localhost:9090/web/dashboard.html")
(def TEST-TIMEOUT 5000)

;; Phase 1: Dashboard Functionality Testing
(defn test-create-session-button
  "Test Create Session button functionality"
  [driver]
  (println "🧪 Testing Create Session Button")
  (println "================================")
  
  ;; Check button exists
  (if (e/exists? driver {:css ".btn-success"})
    (do
      (println "✅ Create Session button found")
      
      ;; Override prompt to provide test name automatically
      (e/js-execute driver "
        window.originalPrompt = window.prompt;
        window.prompt = function(message) {
          console.log('Prompt intercepted:', message);
          return 'automated-test-session-' + Date.now();
        };
      ")
      
      ;; Click the button
      (println "🖱️  Clicking Create Session button...")
      (e/click driver {:css ".btn-success"})
      (Thread/sleep 2000)
      
      ;; Check for success message
      (let [success-msg (e/exists? driver {:css ".alert-success, .success-message"})]
        (println (str "Success message displayed: " success-msg)))
      
      ;; Check console logs
      (println "📋 Console logs:")
      (let [logs (e/get-logs driver :browser)]
        (doseq [log (take 5 logs)]
          (println (str "  " (:level log) ": " (:message log)))))
      
      (println "✅ Create Session button test completed"))
    (println "❌ Create Session button not found")))

(defn test-session-listing
  "Test session listing and display functionality"
  [driver]
  (println "\n🧪 Testing Session Listing")
  (println "===========================")
  
  ;; Test API call
  (let [api-result (e/js-execute driver "
    return fetch('/api/sessions')
      .then(r => r.json())
      .then(data => ({
        success: true,
        count: data.length,
        sessions: data.map(s => s.name)
      }))
      .catch(e => ({
        success: false,
        error: e.message
      }));")]
    
    (println (str "📊 API test result: " api-result))
    
    (if (get api-result :success)
      (do
        (println (str "✅ Sessions API working - " (get api-result :count) " sessions"))
        (println (str "📋 Session names: " (get api-result :sessions))))
      (println (str "❌ Sessions API failed: " (get api-result :error)))))
  
  ;; Check sessions grid
  (if (e/exists? driver {:css ".sessions-grid"})
    (do
      (println "✅ Sessions grid found")
      (let [grid-content (e/get-element-attr driver {:css ".sessions-grid"} "innerHTML")]
        (println (str "📋 Grid content length: " (count grid-content)))
        (if (> (count grid-content) 100)
          (println "✅ Sessions grid has content")
          (println "⚠️  Sessions grid appears empty"))))
    (println "❌ Sessions grid not found")))

(defn test-api-endpoints
  "Test all API endpoints"
  [driver]
  (println "\n🧪 Testing API Endpoints")
  (println "=========================")
  
  ;; Test sessions endpoint
  (let [sessions-test (e/js-execute driver "
    return fetch('/api/sessions')
      .then(r => ({
        status: r.status,
        ok: r.ok,
        contentType: r.headers.get('content-type')
      }))
      .catch(e => ({error: e.message}));")]
    (println (str "📊 /api/sessions: " sessions-test)))
  
  ;; Test system status endpoint
  (let [status-test (e/js-execute driver "
    return fetch('/api/system/status')
      .then(r => ({
        status: r.status,
        ok: r.ok,
        contentType: r.headers.get('content-type')
      }))
      .catch(e => ({error: e.message}));")]
    (println (str "⚡ /api/system/status: " status-test)))
  
  (println "✅ API endpoints test completed"))

(defn test-ui-responsiveness
  "Test UI responsiveness and interactions"
  [driver]
  (println "\n🧪 Testing UI Responsiveness")
  (println "=============================")
  
  ;; Test page load time
  (let [start-time (System/currentTimeMillis)]
    (e/refresh driver)
    (Thread/sleep 1000)
    (let [load-time (- (System/currentTimeMillis) start-time)]
      (println (str "⏱️  Page refresh time: " load-time "ms"))))
  
  ;; Test element visibility
  (let [elements [".container" ".sessions-grid" ".btn-success" ".system-info"]]
    (doseq [element elements]
      (let [visible (e/exists? driver {:css element})]
        (println (str "👁️  " element " visible: " visible)))))
  
  ;; Test button interactions
  (if (e/exists? driver {:css ".btn-info"})
    (do
      (println "🖱️  Testing refresh button...")
      (e/click driver {:css ".btn-info"})
      (Thread/sleep 1000)
      (println "✅ Refresh button clicked"))
    (println "⚠️  Refresh button not found"))
  
  (println "✅ UI responsiveness test completed"))

(defn run-phase1-tests
  "Run all Phase 1 dashboard functionality tests"
  [driver]
  (println "🎯 PHASE 1: DASHBOARD FUNCTIONALITY TESTING")
  (println "===========================================")
  
  ;; Navigate to dashboard
  (e/go driver DASHBOARD-URL)
  (Thread/sleep 3000)
  
  ;; Take initial screenshot
  (e/screenshot driver "phase1-dashboard-initial.png")
  
  ;; Run all tests
  (test-create-session-button driver)
  (test-session-listing driver)
  (test-api-endpoints driver)
  (test-ui-responsiveness driver)
  
  ;; Take final screenshot
  (e/screenshot driver "phase1-dashboard-final.png")
  
  (println "\n🎉 PHASE 1 TESTING COMPLETED!")
  (println "==============================")
  (println "📸 Screenshots saved:")
  (println "  - phase1-dashboard-initial.png")
  (println "  - phase1-dashboard-final.png"))

(defn interactive-phase1-testing
  "Interactive Phase 1 testing with browser control"
  []
  (println "🎮 INTERACTIVE PHASE 1 TESTING")
  (println "===============================")
  
  (let [driver (e/chrome {:headless false})]
    (try
      (run-phase1-tests driver)
      
      (println "\n⏸️  Browser will stay open for manual inspection...")
      (println "📋 Press Enter to continue to next phase or close...")
      (read-line)
      
      (catch Exception e
        (println (str "❌ Error during Phase 1 testing: " (.getMessage e)))
        (e/screenshot driver "phase1-error.png"))
      (finally
        (e/quit driver)
        (println "✅ Phase 1 testing session ended")))))
