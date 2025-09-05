(ns qq.monkeyq.pending-viewer
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def checkpoints-dir "monkeyq-checkpoints")

(defn show-pending-vv-feedback []
  "Show pending VV feedback with parent context from all checkpoints"
  (println "📋 Pending VV Feedback (2025-09-05 19:33)")
  (println "=" (apply str (repeat 50 "=")))
  
  (let [checkpoints-root (io/file checkpoints-dir)]
    (if (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort)
                     (reverse))] ; Latest first
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(re-find #"block-.*\.json" (.getName %))))]
            (doseq [file block-files]
              (let [block (json/read-str (slurp file) :key-fn keyword)]
                (when (and (or (str/includes? (:string block) "#VVFeedback")
                              (str/includes? (:string block) "#VVRequest"))
                          (not= (get block :response-status) "responded")
                          (not= (get block :response-status) "no-response-needed"))
                  (println "🔸" (:uid block) "(" dir ")")
                  (println "  VV Feedback:" (:string block))
                  (when (:parent block)
                    (println "  📝 Context:" (get (:parent block) :parent-string "No context")))
                  (println)))))))
      (println "📭 No checkpoints found"))))
