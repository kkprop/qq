(load-file "src/qq/tui.clj")

(defn debug-key-input []
  (println "Debug key input - press keys (q to quit):")
  (qq.tui/setup-raw-mode)
  (try
    (loop []
      (let [key (qq.tui/check-key-press)]
        (when key
          (println "Key detected:" key "Type:" (type key)))
        (when-not (= key :q)
          (Thread/sleep 50)
          (recur))))
    (finally
      (qq.tui/restore-terminal))))

(debug-key-input)
