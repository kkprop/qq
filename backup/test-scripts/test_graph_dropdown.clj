(ns qq.browser.test-graph-dropdown
  "Test the Select Graph dropdown specifically"
  (:require [etaoin.api :as e]
            [clojure.string :as str]))

(defn test-graph-dropdown
  "Test the Select Graph dropdown for voice-assistant-star"
  []
  (println "🎯 Testing Select Graph dropdown...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Navigate to TEN Agent
      (println "📍 Navigating to TEN Agent...")
      (e/go driver "http://localhost:3000")
      (Thread/sleep 5000)
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/graph-test-1-initial.png")
      
      ;; Look for the Select Graph button
      (println "🔍 Looking for Select Graph button...")
      (if (e/exists? driver "[role='combobox']")
        (do
          (println "✅ Found Select Graph combobox!")
          
          ;; Click to open dropdown
          (println "🖱️ Clicking to open dropdown...")
          (e/click driver "[role='combobox']")
          (Thread/sleep 3000)
          
          ;; Take screenshot of opened dropdown
          (e/screenshot driver "screenshots/graph-test-2-dropdown-open.png")
          
          ;; Look for dropdown options
          (let [options (e/query-all driver "[role='option'], .option, li, [data-value]")]
            (println (str "📋 Found " (count options) " potential options"))
            
            ;; Try to get text from options
            (doseq [i (range (min 10 (count options)))]
              (try
                (let [option (nth options i)
                      text (e/get-element-text driver option)]
                  (when (not (str/blank? text))
                    (println (str "  📄 Option " (inc i) ": '" text "'"))
                    ;; Check if this is our voice-assistant-star
                    (when (or (str/includes? (str/lower-case text) "voice")
                              (str/includes? (str/lower-case text) "assistant")
                              (str/includes? (str/lower-case text) "star"))
                      (println "    🎤 ⭐ FOUND VOICE ASSISTANT OPTION!"))))
                (catch Exception e
                  (println (str "    ⚠️ Could not read option " (inc i) ": " (.getMessage e))))))
            
            ;; Also check for any elements with text containing "voice" or "assistant"
            (println "\n🔍 Searching for voice/assistant related elements...")
            (let [voice-elements (e/query-all driver "*")]
              (doseq [elem voice-elements]
                (try
                  (let [text (e/get-element-text driver elem)]
                    (when (and (not (str/blank? text))
                               (< (count text) 100)
                               (or (str/includes? (str/lower-case text) "voice")
                                   (str/includes? (str/lower-case text) "assistant")))
                      (println (str "  🎯 Found: '" text "'"))))
                  (catch Exception e
                    ;; Ignore elements we can't read
                    nil))))
            
            options))
        
        (do
          (println "❌ Select Graph combobox not found")
          ;; Look for any button with "graph" in it
          (let [graph-buttons (e/query-all driver "button, [role='button']")]
            (println (str "🔍 Found " (count graph-buttons) " buttons, checking for graph-related ones..."))
            (doseq [i (range (min 5 (count graph-buttons)))]
              (try
                (let [btn (nth graph-buttons i)
                      text (e/get-element-text driver btn)]
                  (when (and (not (str/blank? text))
                             (str/includes? (str/lower-case text) "graph"))
                    (println (str "  📄 Graph button: '" text "'"))))
                (catch Exception e
                  nil))))))
      
      ;; Take final screenshot
      (e/screenshot driver "screenshots/graph-test-3-final.png")
      
      (println "\n🔄 Keeping browser open for 30 seconds for manual inspection...")
      (Thread/sleep 30000)
      
      (catch Exception e
        (println (str "\n❌ Test failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/graph-test-error.png"))
      
      (finally
        (e/quit driver)))))

(defn -main [& args]
  (test-graph-dropdown))
