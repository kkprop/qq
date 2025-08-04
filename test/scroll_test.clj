(ns scroll-test
  (:require [etaoin.api :as e]
            [clojure.test :refer :all]))

(deftest test-terminal-scroll-functionality
  "Test that the terminal scroll functionality works properly"
  (let [driver (e/chrome {:headless false :size [1200 800]})]
    (try
      (println "🧪 Starting scroll functionality test...")
      
      ;; Navigate to terminal
      (e/go driver "http://localhost:9090/web/terminal-simple.html")
      (e/wait 3)
      
      ;; Wait for terminal to load
      (println "⏳ Waiting for terminal to initialize...")
      (e/wait-visible driver {:id "terminal"})
      (e/wait 2)
      
      ;; Check if terminal content is present
      (println "🔍 Checking for terminal content...")
      (let [terminal-element (e/query driver {:id "terminal"})]
        (is (some? terminal-element) "Terminal element should be present"))
      
      ;; Look for the xterm viewport
      (println "🔍 Looking for xterm viewport...")
      (e/wait-visible driver {:class "xterm-viewport"} {:timeout 10})
      
      (let [viewport (e/query driver {:class "xterm-viewport"})]
        (is (some? viewport) "Xterm viewport should be present")
        
        ;; Get initial scroll position
        (println "📊 Getting initial scroll position...")
        (let [initial-scroll-top (e/js-execute driver "return document.querySelector('.xterm-viewport').scrollTop")
              scroll-height (e/js-execute driver "return document.querySelector('.xterm-viewport').scrollHeight")
              client-height (e/js-execute driver "return document.querySelector('.xterm-viewport').clientHeight")]
          
          (println (str "📊 Initial state: scrollTop=" initial-scroll-top 
                       ", scrollHeight=" scroll-height 
                       ", clientHeight=" client-height))
          
          ;; Test if content is scrollable
          (is (> scroll-height client-height) "Content should be taller than viewport (scrollable)")
          
          ;; Test scroll to top
          (println "🔝 Testing scroll to top...")
          (e/js-execute driver "document.querySelector('.xterm-viewport').scrollTop = 0")
          (e/wait 1)
          
          (let [scroll-top-after-top (e/js-execute driver "return document.querySelector('.xterm-viewport').scrollTop")]
            (println (str "📊 After scroll to top: scrollTop=" scroll-top-after-top))
            (is (= 0 scroll-top-after-top) "Should be able to scroll to top"))
          
          ;; Test scroll to bottom
          (println "🔽 Testing scroll to bottom...")
          (e/js-execute driver "document.querySelector('.xterm-viewport').scrollTop = document.querySelector('.xterm-viewport').scrollHeight")
          (e/wait 1)
          
          (let [scroll-top-after-bottom (e/js-execute driver "return document.querySelector('.xterm-viewport').scrollTop")
                max-scroll (- scroll-height client-height)]
            (println (str "📊 After scroll to bottom: scrollTop=" scroll-top-after-bottom ", maxScroll=" max-scroll))
            (is (>= scroll-top-after-bottom (- max-scroll 10)) "Should be able to scroll to bottom"))
          
          ;; Test scroll events
          (println "📜 Testing scroll events...")
          (e/js-execute driver "
            window.scrollEventCount = 0;
            document.querySelector('.xterm-viewport').addEventListener('scroll', function() {
              window.scrollEventCount++;
              console.log('Scroll event fired, count:', window.scrollEventCount);
            });
          ")
          
          ;; Trigger scroll and check if events fire
          (e/js-execute driver "document.querySelector('.xterm-viewport').scrollTop = 100")
          (e/wait 1)
          (e/js-execute driver "document.querySelector('.xterm-viewport').scrollTop = 200")
          (e/wait 1)
          
          (let [scroll-event-count (e/js-execute driver "return window.scrollEventCount || 0")]
            (println (str "📊 Scroll events fired: " scroll-event-count))
            (is (> scroll-event-count 0) "Scroll events should fire when scrolling"))
          
          ;; Test manual scrolling with mouse wheel (if possible)
          (println "🖱️ Testing mouse wheel scrolling...")
          (try
            (e/scroll-down driver {:class "xterm-viewport"})
            (e/wait 1)
            (let [scroll-after-wheel (e/js-execute driver "return document.querySelector('.xterm-viewport').scrollTop")]
              (println (str "📊 After mouse wheel: scrollTop=" scroll-after-wheel))
              (is (> scroll-after-wheel 200) "Mouse wheel should change scroll position"))
            (catch Exception e
              (println "⚠️ Mouse wheel test failed (might not be supported):" (.getMessage e))))
          
          (println "✅ Scroll functionality test completed!")))
      
      (catch Exception e
        (println (str "❌ Test failed: " (.getMessage e)))
        (is false (str "Test should not throw exception: " (.getMessage e))))
      
      (finally
        (e/quit driver)))))

(defn run-scroll-test []
  "Run the scroll test"
  (println "🚀 Running scroll functionality test...")
  (test-terminal-scroll-functionality)
  (println "🎯 Scroll test completed!"))
