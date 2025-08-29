(ns qq.browser.interactive-tman-test
  "Interactive test of TMAN Designer with clicking and interaction"
  (:require [etaoin.api :as e]
            [clojure.string :as str]))

(defn interactive-tman-test
  "Interactive test of TMAN Designer with clicking"
  []
  (println "🎯 Interactive TMAN Designer Test...")
  
  (let [driver (e/chrome {:headless false})]
    (try
      ;; Navigate to TMAN Designer
      (println "📍 Navigating to TMAN Designer...")
      (e/go driver "http://localhost:49483")
      (Thread/sleep 10000) ; Wait longer for full load
      
      ;; Take initial screenshot
      (e/screenshot driver "screenshots/interactive-1-loaded.png")
      (println "📸 Initial screenshot taken")
      
      ;; Check page title
      (let [title (e/get-title driver)]
        (println (str "📄 Page title: '" title "'")))
      
      ;; Look for clickable elements
      (println "🔍 Looking for clickable elements...")
      
      ;; Try to find buttons, links, or clickable divs
      (let [buttons (e/query-all driver "button")
            links (e/query-all driver "a")
            clickable-divs (e/query-all driver "div[onclick], div[role='button']")
            all-clickable (concat buttons links clickable-divs)]
        
        (println (str "🔘 Found " (count buttons) " buttons"))
        (println (str "🔗 Found " (count links) " links"))
        (println (str "📦 Found " (count clickable-divs) " clickable divs"))
        (println (str "👆 Total clickable elements: " (count all-clickable)))
        
        ;; Try to get text from first few clickable elements
        (doseq [i (range (min 5 (count all-clickable)))]
          (try
            (let [elem (nth all-clickable i)
                  text (e/get-element-text driver elem)]
              (when (not (str/blank? text))
                (println (str "  📄 Clickable " (inc i) ": '" text "'"))))
            (catch Exception e
              (println (str "  ⚠️ Could not read element " (inc i)))))))
      
      ;; Look for specific TMAN Designer elements
      (println "🔍 Looking for TMAN Designer specific elements...")
      
      ;; Look for graph-related elements
      (if (e/exists? driver "[class*='graph'], [class*='canvas'], [class*='flow']")
        (do
          (println "✅ Found graph/canvas area")
          (e/screenshot driver "screenshots/interactive-2-graph-found.png"))
        (println "❌ No graph area found"))
      
      ;; Look for run/play buttons
      (let [play-buttons (e/query-all driver "button[title*='run'], button[title*='play'], [class*='play'], [class*='run']")]
        (if (> (count play-buttons) 0)
          (do
            (println (str "✅ Found " (count play-buttons) " potential run/play buttons"))
            (try
              (println "🖱️ Attempting to click first run button...")
              (e/click driver (first play-buttons))
              (Thread/sleep 3000)
              (e/screenshot driver "screenshots/interactive-3-after-click.png")
              (println "✅ Clicked run button successfully")
              (catch Exception e
                (println (str "❌ Failed to click: " (.getMessage e)))))
          (println "❌ No run/play buttons found")))
      
      ;; Look for any text inputs or configuration areas
      (let [inputs (e/query-all driver "input, textarea")]
        (println (str "📝 Found " (count inputs) " input fields")))
      
      ;; Take final screenshot
      (e/screenshot driver "screenshots/interactive-4-final.png")
      (println "📸 Final screenshot taken")
      
      (println "\n🔄 Keeping browser open for 60 seconds for manual inspection...")
      (println "   You can now manually interact with the TMAN Designer!")
      (Thread/sleep 60000)
      
      (catch Exception e
        (println (str "\n❌ Test failed: " (.getMessage e)))
        (e/screenshot driver "screenshots/interactive-error.png"))
      
      (finally
        (e/quit driver))))))

(defn -main [& args]
  (interactive-tman-test))
