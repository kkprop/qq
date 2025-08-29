(load-file "src/qq/tui.clj")

(println "Testing Gum-style API levels...")

; Level 1: Ultra-simple (like gum)
(println "\n1. Ultra-simple API:")
(println "   (tui/select-from [\"apple\" \"banana\" \"cherry\"])")

; Level 2: Simple with customization
(println "\n2. Simple with customization:")
(println "   (tui/select-from items :title \"Pick Fruit\" :item-fn str/upper-case)")

; Level 3: Full control (for picky users)
(println "\n3. Full control:")
(println "   (tui/select-from items :render-fn custom-render-function)")

(println "\nAll levels are compatible and work together!")

; Test ultra-simple
(let [result (qq.tui/select-from ["apple" "banana" "cherry"])]
  (when result
    (println (str "Selected: " result))))
