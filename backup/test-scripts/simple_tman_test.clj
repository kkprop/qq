(ns qq.browser.simple-tman-test
  "Simple interactive TMAN Designer test"
  (:require [etaoin.api :as e]))

(defn simple-tman-test
  "Simple test of TMAN Designer with interaction"
  []
  (println "🎯 Simple TMAN Designer Test...")
  
  (let [driver (e/chrome {:headless false})]
    (println "📍 Navigating to TMAN Designer...")
    (e/go driver "http://localhost:49483")
    (Thread/sleep 10000)
    
    (println "📸 Taking screenshot...")
    (e/screenshot driver "screenshots/simple-tman-1.png")
    
    (println "🔍 Looking for buttons...")
    (let [buttons (e/query-all driver "button")]
      (println (str "Found " (count buttons) " buttons")))
    
    (println "🔍 Looking for any clickable elements...")
    (let [clickable (e/query-all driver "button, a, [role='button'], [onclick]")]
      (println (str "Found " (count clickable) " clickable elements"))
      
      (when (> (count clickable) 0)
        (println "📄 First few clickable elements:")
        (doseq [i (range (min 3 (count clickable)))]
          (try
            (let [elem (nth clickable i)
                  tag (e/get-element-tag driver elem)]
              (println (str "  " (inc i) ". <" tag ">")))
            (catch Exception e
              (println (str "  " (inc i) ". (could not read)")))))))
    
    (println "📸 Taking final screenshot...")
    (e/screenshot driver "screenshots/simple-tman-2.png")
    
    (println "🔄 Keeping browser open for 30 seconds...")
    (Thread/sleep 30000)
    
    (e/quit driver)))

(defn -main [& args]
  (simple-tman-test))
