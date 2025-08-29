(load-file "src/qq/tui.clj")

(defn test-q-key []
  (println "Testing 'q' key detection:")
  (qq.tui/setup-raw-mode)
  (try
    (println "Press 'q' key:")
    (loop []
      (let [key (qq.tui/check-key-press)]
        (when key
          (println "Key:" key "Char?:" (char? key) "Equal to \\q?:" (= key \q))
          (when (= key \q)
            (println "SUCCESS: 'q' detected as character!")))
        (when-not (= key :ctrl-c)
          (Thread/sleep 50)
          (recur))))
    (finally
      (qq.tui/restore-terminal))))

(test-q-key)
