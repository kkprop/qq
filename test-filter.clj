(load-file "src/qq/tui.clj")

(defn test-fuzzy-match []
  (println "Testing fuzzy matching:")
  (println "  'abc' matches 'alphabet':" (qq.tui/fuzzy-match "abc" "alphabet"))
  (println "  'xyz' matches 'alphabet':" (qq.tui/fuzzy-match "xyz" "alphabet"))
  (println "  'alp' matches 'alphabet':" (qq.tui/fuzzy-match "alp" "alphabet"))
  (println "  '' matches 'alphabet':" (qq.tui/fuzzy-match "" "alphabet")))

(defn test-filter-items []
  (let [items ["apple" "banana" "cherry" "date" "elderberry"]]
    (println "\nTesting item filtering:")
    (println "  Items:" items)
    (println "  Filter 'a':" (qq.tui/filter-items "a" items))
    (println "  Filter 'an':" (qq.tui/filter-items "an" items))
    (println "  Filter 'e':" (qq.tui/filter-items "e" items))
    (println "  Filter '':" (qq.tui/filter-items "" items))))

(test-fuzzy-match)
(test-filter-items)
