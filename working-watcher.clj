#!/usr/bin/env bb

(require '[clojure.string :as str])

(defn extract-user-questions-simple [content]
  "Extract user questions using simple line-based approach"
  (->> (str/split-lines content)
       (filter #(str/ends-with? % "\u001B[?2004l"))  ; Lines ending with this are user input
       (map #(-> %
                (str/replace #"\u001B\[[0-9;]*m" "")   ; Remove ANSI color codes
                (str/replace #"\u001B\[\?[0-9]+l" "")  ; Remove control sequences  
                (str/replace #"\r" "")                 ; Remove carriage returns
                (str/trim)))                           ; Trim whitespace
       (filter #(> (count %) 1))                      ; Filter out empty/short lines
       (distinct)))                                   ; Remove duplicates

(defn test-simple-extraction []
  "Test simple extraction on actual tmux output"
  (println "🧪 Testing simple line-based extraction...")
  
  (let [stream-file "/tmp/qq-stream-qq-q.log"
        content (slurp stream-file)
        questions (extract-user-questions-simple content)]
    
    (println "\n📊 Found" (count questions) "user questions:")
    (doseq [q questions]
      (println "  📝" q))
    
    (println "\n🎯 This approach is much simpler than regex!")))

;; Run the test
(test-simple-extraction)
