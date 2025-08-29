(load-file "src/qq/tui.clj")

(defn render-simple [state]
  (let [{:keys [items selected query cursor-visible]} state
        filtered-items (qq.tui/filter-items (or query "") items)
        header "🎯 SMART API TEST\r\n================\r\n"
        cursor (if cursor-visible "█" " ")
        search-line (str "🔍 Filter: " (or query "") cursor "\r\n")
        item-list (clojure.string/join "\r\n" 
                    (map-indexed 
                      (fn [idx item]
                        (let [prefix (if (= idx selected) "► " "  ")]
                          (str prefix item)))
                      filtered-items))
        footer "\r\n\r\nStatic list test - no refresh"]
    (str header search-line item-list footer)))

; Test 1: Static data (vector)
(println "Testing static data...")
(let [result (qq.tui/select-from ["apple" "banana" "cherry"] render-simple)]
  (println "Selected:" result))

; Test 2: Dynamic function
(println "\nTesting dynamic function...")
(defn get-items [] ["dynamic1" "dynamic2" "dynamic3"])
(let [result (qq.tui/select-from get-items render-simple)]
  (println "Selected:" result))
