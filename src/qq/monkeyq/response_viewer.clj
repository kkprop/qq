(ns qq.monkeyq.response-viewer
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [qq.tui :as tui]))

(def checkpoints-dir "monkeyq-checkpoints")

(defn find-local-response-file [response-uid vv-feedback]
  "Find local markdown file for this response"
  (let [drafts-dir "drafts/vv-responses/"
        files (when (.exists (io/file drafts-dir))
                (->> (io/file drafts-dir)
                     .listFiles
                     (filter #(str/ends-with? (.getName %) ".md"))))]
    (when files
      (first (filter (fn [file]
                      (let [content (slurp file)]
                        (or (str/includes? content response-uid)
                            (str/includes? content (subs vv-feedback 0 (min 50 (count vv-feedback))))
                            (str/includes? (.getName file) "vv-"))))
                    files)))))

(defn get-all-responses []
  "Get all responded VV feedback"
  (let [checkpoints-root (io/file checkpoints-dir)
        responses (atom [])]
    (when (.exists checkpoints-root)
      (let [dirs (->> (.listFiles checkpoints-root)
                     (filter #(.isDirectory %))
                     (map #(.getName %))
                     (sort))]
        (doseq [dir dirs]
          (let [checkpoint-path (str checkpoints-dir "/" dir)
                block-files (->> (io/file checkpoint-path)
                                .listFiles
                                (filter #(str/starts-with? (.getName %) "block-")))]
            (doseq [block-file block-files]
              (let [block (json/read-str (slurp block-file) :key-fn keyword)]
                (when (and (or (str/includes? (:string block) "#VVFeedback")
                              (str/includes? (:string block) "#VVRequest"))
                          (= (get block :response-status) "responded"))
                  (let [local-file (find-local-response-file (:response-uid block) (:string block))]
                    (swap! responses conj {:uid (:uid block)
                                          :feedback (:string block)
                                          :response-uid (:response-uid block)
                                          :response-date (:response-date block)
                                          :context (get (:parent block) :parent-string "")
                                          :local-file local-file
                                          :dir dir})))))))))
    @responses))

(defn show-responses-tui []
  "Show responses with professional TUI selection"
  (let [responses (get-all-responses)
        sorted-responses (reverse (sort-by :response-date responses))]
    (if (empty? sorted-responses)
      (println "📭 No responses found")
      (do
        (println "📋 VV Response History (2025-09-05 19:33)")
        (println "=" (apply str (repeat 50 "=")))
        (let [item-fn (fn [response]
                       (let [feedback-preview (subs (:feedback response) 0 (min 60 (count (:feedback response))))
                             file-info (if (:local-file response) 
                                        (str " [" (.getName (:local-file response)) "]")
                                        " [no file]")
                             date-info (str " (" (:response-date response) ")")]
                         (str (:uid response) " - " feedback-preview "..." file-info date-info)))
              selected (tui/select-from sorted-responses 
                                       :title "🎯 Select response to view details (latest first):"
                                       :item-fn item-fn)]
          (when selected
            (println "\n📄 Response Details for" (:uid selected) ":")
            (println "=" (apply str (repeat 60 "=")))
            (println "📋 VV Feedback:" (:feedback selected))
            (println "🎯 Response UID:" (:response-uid selected))
            (println "📅 Response Date:" (:response-date selected))
            (when (not (str/blank? (:context selected)))
              (println "🔗 Context:" (:context selected)))
            (if (:local-file selected)
              (do
                (println "📁 Local File:" (.getPath (:local-file selected)))
                (println "\n📝 Content:")
                (println (slurp (:local-file selected))))
              (println "❌ No local file found for this response"))))))))
