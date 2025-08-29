(ns qq.browser.ten-agent-test
  "TEN Agent Playground Browser Automation Test"
  (:require [etaoin.api :as e]
            [clojure.string :as str]))

;; TEN Agent URLs
(def PLAYGROUND-URL "http://localhost:3000")
(def TMAN-DESIGNER-URL "http://localhost:49483")

(defn test-ten-agent-playground
  "Test TEN Agent Playground interface with Deepgram TTS2 integration"
  []
  (println "🚀 Starting TEN Agent Playground automation test...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Step 1: Navigate to playground
      (println "📍 Step 1: Navigating to TEN Agent Playground...")
      (e/go driver PLAYGROUND-URL)
      
      ;; Wait for React app to load - look for specific TEN Agent elements
      (println "⏳ Waiting for React app to load...")
      (e/wait-visible driver "h1" {:timeout 30})  ; Wait for the TEN Agent header
      (e/screenshot driver "screenshots/ten-agent-1-playground-loaded.png")
      (println "✅ Playground loaded")
      
      ;; Step 2: Check for key elements
      (println "📍 Step 2: Checking playground elements...")
      (let [has-header (e/exists? driver "h1")
            has-connect-btn (e/exists? driver "button:contains('Connect')")
            has-settings (e/exists? driver "[aria-label*='settings'], .lucide-settings")
            title-text (e/get-element-text driver "h1")]
        (println (str "  Header: " has-header " - Text: " title-text))
        (println (str "  Connect button: " has-connect-btn))
        (println (str "  Settings: " has-settings)))
      
      ;; Step 3: Look for graph selection dropdown
      (println "📍 Step 3: Looking for graph selection...")
      (when (e/exists? driver "button:contains('Select Graph')")
        (println "✅ Found graph selection dropdown")
        (e/click driver "button:contains('Select Graph')")
        (Thread/sleep 1000)  ; Wait for dropdown to open
        (e/screenshot driver "screenshots/ten-agent-graph-dropdown.png"))
      
      ;; Step 4: Check for voice/audio controls and tabs
      (println "📍 Step 4: Checking for interface elements...")
      (let [agent-tab (e/exists? driver "button[role='tab']:contains('Agent')")
            chat-tab (e/exists? driver "button[role='tab']:contains('Chat')")
            github-link (e/exists? driver "a[href*='github.com']")]
        (println (str "  Agent tab: " agent-tab))
        (println (str "  Chat tab: " chat-tab))
        (println (str "  GitHub link: " github-link)))
      
      ;; Step 5: Take final screenshot
      (e/screenshot driver "screenshots/ten-agent-2-elements-checked.png")
      (println "📸 Screenshots saved to screenshots/")
      
      (println "✅ TEN Agent Playground test completed successfully!")
      
      (catch Exception e
        (println (str "❌ Test failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/ten-agent-error.png"))
      
      (finally
        (e/quit driver)))))

(defn test-tman-designer
  "Test TMAN Designer interface"
  []
  (println "🚀 Starting TMAN Designer automation test...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Step 1: Navigate to TMAN Designer
      (println "📍 Step 1: Navigating to TMAN Designer...")
      (e/go driver TMAN-DESIGNER-URL)
      
      ;; Wait for any content to load
      (println "⏳ Waiting for TMAN Designer to load...")
      (e/wait-visible driver "body" {:timeout 30})
      (Thread/sleep 3000)  ; Give it extra time to load
      (e/screenshot driver "screenshots/tman-1-designer-loaded.png")
      (println "✅ TMAN Designer loaded")
      
      ;; Step 2: Check page title and basic structure
      (println "📍 Step 2: Checking page structure...")
      (let [page-title (e/get-title driver)
            has-body (e/exists? driver "body")
            body-content (e/get-element-text driver "body")]
        (println (str "  Page title: " page-title))
        (println (str "  Has body: " has-body))
        (println (str "  Body content length: " (count body-content))))
      
      ;; Step 3: Look for any interactive elements
      (println "📍 Step 3: Looking for interactive elements...")
      (let [buttons (count (e/query-all driver "button"))
            inputs (count (e/query-all driver "input"))
            links (count (e/query-all driver "a"))]
        (println (str "  Buttons found: " buttons))
        (println (str "  Inputs found: " inputs))
        (println (str "  Links found: " links)))
      
      ;; Step 4: Check for API endpoints or data
      (println "📍 Step 4: Checking for API connectivity...")
      (let [api-test (e/js-execute driver 
                       "return fetch('/api/designer/v1/graphs')
                         .then(r => r.status)
                         .catch(() => 'failed')")]
        (println (str "  API Status Code: " api-test)))
      
      ;; Step 5: Take final screenshot
      (e/screenshot driver "screenshots/tman-2-elements-checked.png")
      (println "📸 Screenshots saved to screenshots/")
      
      (println "✅ TMAN Designer test completed successfully!")
      
      (catch Exception e
        (println (str "❌ Test failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/tman-error.png"))
      
      (finally
        (e/quit driver)))))

(defn test-deepgram-integration
  "Test Deepgram TTS2 integration specifically"
  []
  (println "🚀 Starting Deepgram TTS2 integration test...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Navigate to playground first
      (e/go driver PLAYGROUND-URL)
      (e/wait-visible driver "h1" {:timeout 30})
      
      ;; Look for TTS/audio related elements
      (println "📍 Checking for TTS/Audio elements...")
      (let [audio-elements (e/query-all driver "audio, .audio, [data-testid*='audio']")
            tts-elements (e/query-all driver "[data-testid*='tts'], .tts, button[aria-label*='speak']")
            mic-elements (e/query-all driver "[aria-label*='mic'], .mic, button[aria-label*='microphone']")]
        (println (str "  Audio elements: " (count audio-elements)))
        (println (str "  TTS elements: " (count tts-elements)))
        (println (str "  Microphone elements: " (count mic-elements))))
      
      ;; Check browser console for Deepgram related logs
      (println "📍 Checking console for Deepgram logs...")
      (let [logs (e/get-logs driver :browser)]
        (doseq [log logs]
          (when (str/includes? (str/lower-case (:message log)) "deepgram")
            (println (str "  Deepgram log: " (:message log))))))
      
      ;; Check for WebSocket connections (common for real-time audio)
      (println "📍 Checking for WebSocket activity...")
      (let [ws-check (e/js-execute driver 
                       "return window.WebSocket ? 'WebSocket supported' : 'No WebSocket'")]
        (println (str "  WebSocket support: " ws-check)))
      
      (e/screenshot driver "screenshots/deepgram-integration-test.png")
      (println "✅ Deepgram integration test completed!")
      
      (catch Exception e
        (println (str "❌ Deepgram test failed: " (.getMessage e))))
      
      (finally
        (e/quit driver)))))

(defn run-all-tests
  "Run all TEN Agent automation tests"
  []
  (println "🎯 Running complete TEN Agent automation test suite...")
  (test-ten-agent-playground)
  (Thread/sleep 2000)
  (test-tman-designer)
  (Thread/sleep 2000)
  (test-deepgram-integration)
  (println "🎉 All TEN Agent tests completed!"))

;; Main execution
(defn -main [& args]
  (case (first args)
    "playground" (test-ten-agent-playground)
    "tman" (test-tman-designer)
    "deepgram" (test-deepgram-integration)
    "all" (run-all-tests)
    (do
      (println "Usage: bb ten-agent-test [playground|tman|deepgram|all]")
      (println "  playground - Test TEN Agent Playground")
      (println "  tman       - Test TMAN Designer")
      (println "  deepgram   - Test Deepgram integration")
      (println "  all        - Run all tests"))))
