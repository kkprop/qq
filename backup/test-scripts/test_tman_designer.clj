(ns qq.browser.test-tman-designer
  "Test TMAN Designer interface"
  (:require [etaoin.api :as e]
            [clojure.string :as str]))

(defn test-tman-designer
  "Test TMAN Designer at port 49483"
  []
  (println "🎯 Testing TMAN Designer...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Navigate to TMAN Designer
      (println "📍 Navigating to TMAN Designer...")
      (e/go driver "http://localhost:49483")
      (Thread/sleep 8000)
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/tman-designer-1-initial.png")
      (println "📸 Initial screenshot taken")
      
      ;; Check page title
      (let [title (e/get-title driver)]
        (println (str "📄 Page title: '" title "'")))
      
      ;; Look for main elements
      (println "🔍 Looking for main interface elements...")
      
      ;; Check for canvas or graph area
      (if (e/exists? driver "canvas")
        (println "✅ Found canvas element (graph area)")
        (println "❌ No canvas found"))
      
      ;; Check for buttons
      (let [buttons (e/query-all driver "button")]
        (println (str "🔘 Found " (count buttons) " buttons")))
      
      ;; Check for any text mentioning "voice" or "assistant"
      (println "🔍 Looking for voice assistant related content...")
      (let [body-text (e/get-element-text driver "body")]
        (when (or (str/includes? (str/lower-case body-text) "voice")
                  (str/includes? (str/lower-case body-text) "assistant"))
          (println "✅ Found voice assistant related content")))
      
      ;; Take final screenshot
      (e/screenshot driver "screenshots/tman-designer-2-final.png")
      (println "📸 Final screenshot taken")
      
      (println "\n🔄 Keeping browser open for 60 seconds for manual inspection...")
      (Thread/sleep 60000)
      
      (catch Exception e
        (println (str "\n❌ Test failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/tman-designer-error.png"))
      
      (finally
        (e/quit driver)))))

(defn -main [& args]
  (test-tman-designer))
