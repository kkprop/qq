(load-file "src/qq/tui.clj")

(defn format-item [item selected?]
  (let [prefix (if selected? "► " "  ")]
    (str prefix item)))

(defn render-demo [state]
  (let [{:keys [items selected query]} state
        filtered-items (qq.tui/filter-items (or query "") items)
        header "🎯 DEMO FILTER TUI\r\n==================\r\n"
        search-line (str "🔍 Search: " (or query "") "█\r\n")
        item-list (clojure.string/join "\r\n" 
                    (map-indexed 
                      (fn [idx item]
                        (format-item item (= idx selected)))
                      filtered-items))
        footer "\r\n\r\n↑↓/Ctrl+P/Ctrl+N Navigate | Esc Clear | Enter Select | Backspace Delete | Ctrl+C Quit"]
    (str header search-line item-list footer)))

(defn demo-filter []
  (let [items ["apple" "banana" "cherry" "date" "elderberry" "fig" "grape" "honeydew"]
        app (qq.tui/create-app 
              {:items items :selected 0 :query ""}
              render-demo)]
    
    (let [result (qq.tui/start-filter-app app)]
      (when result
        (println (str "\nYou selected: " result))))))

(demo-filter)
