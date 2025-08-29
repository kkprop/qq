(load-file "src/qq/tui.clj")

(println "Testing keywords and default render...")

; Test 1: Function returning keywords
(defn get-keywords [] [:apple :banana :cherry :date])

(println "Test 1: Function returning keywords")
(let [result (qq.tui/select-from get-keywords :title "Pick Keyword")]
  (when result
    (println (str "Selected keyword: " result " (type: " (type result) ")"))))

; Test 2: Static keywords
(println "\nTest 2: Static keywords")
(let [result (qq.tui/select-from [:red :green :blue] :title "Pick Color")]
  (when result
    (println (str "Selected color: " result))))
