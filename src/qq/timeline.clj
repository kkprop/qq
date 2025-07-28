(ns qq.timeline
  "Q&A Timeline tracking for session context generation"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; Timeline data structure for Q&A interactions
;; {
;;   "session_id": "default",
;;   "interactions": [
;;     {
;;       "timestamp": "2025-07-27T12:00:00Z",
;;       "type": "question", 
;;       "content": "What causes Lambda cold starts?",
;;       "user": "human"
;;     },
;;     {
;;       "timestamp": "2025-07-27T12:00:15Z",
;;       "type": "answer",
;;       "content": "Lambda cold starts are caused by...",
;;       "user": "q",
;;       "response_time_ms": 15000
;;     }
;;   ],
;;   "summary": "Discussion about AWS Lambda performance optimization",
;;   "last_updated": "2025-07-27T12:00:15Z"
;; }

(defn get-timeline-file [session-id]
  "Get timeline file path for session"
  (let [base-dir (str (System/getProperty "user.home") "/.knock/qq")
        session-dir (str base-dir "/sessions/" session-id)]
    (io/make-parents (str session-dir "/timeline.json"))
    (str session-dir "/timeline.json")))

(defn load-timeline [session-id]
  "Load timeline data for session, return empty structure if not found"
  (let [timeline-file (get-timeline-file session-id)]
    (try
      (if (.exists (io/file timeline-file))
        (json/read-str (slurp timeline-file) :key-fn keyword)
        ;; Return empty timeline structure
        {:session_id session-id
         :interactions []
         :summary ""
         :last_updated nil})
      (catch Exception e
        (println "Warning: Could not load timeline for session" session-id ":" (.getMessage e))
        {:session_id session-id
         :interactions []
         :summary ""
         :last_updated nil}))))

(defn save-timeline [session-id timeline-data]
  "Save timeline data to disk"
  (let [timeline-file (get-timeline-file session-id)]
    (try
      (spit timeline-file (json/write-str timeline-data {:pretty true}))
      (catch Exception e
        (println "Warning: Could not save timeline for session" session-id ":" (.getMessage e))))))

(defn current-timestamp []
  "Get current timestamp in ISO format"
  (str (java.time.Instant/now)))

(defn log-question [session-id question]
  "Log a question to the session timeline"
  (let [timeline (load-timeline session-id)
        interaction {:timestamp (current-timestamp)
                    :type "question"
                    :content question
                    :user "human"}
        updated-timeline (-> timeline
                           (update :interactions conj interaction)
                           (assoc :last_updated (current-timestamp)))]
    (save-timeline session-id updated-timeline)
    updated-timeline))

(defn log-answer [session-id answer response-time-ms]
  "Log an answer to the session timeline"
  (let [timeline (load-timeline session-id)
        interaction {:timestamp (current-timestamp)
                    :type "answer"
                    :content answer
                    :user "q"
                    :response_time_ms response-time-ms}
        updated-timeline (-> timeline
                           (update :interactions conj interaction)
                           (assoc :last_updated (current-timestamp)))]
    (save-timeline session-id updated-timeline)
    updated-timeline))

(defn log-interaction [session-id question answer response-time-ms]
  "Log both question and answer as a complete interaction"
  (-> (log-question session-id question)
      (log-answer session-id answer response-time-ms)))

(defn get-recent-interactions [session-id limit]
  "Get recent interactions for context generation"
  (let [timeline (load-timeline session-id)]
    (->> (:interactions timeline)
         (take-last (* limit 2)) ; Take last N Q&A pairs (question + answer = 2 interactions)
         vec)))

(defn generate-timeline-summary [session-id]
  "Generate summary of timeline for default session context"
  (let [timeline (load-timeline session-id)
        interactions (:interactions timeline)
        question-count (count (filter #(= (:type %) "question") interactions))
        recent-topics (->> interactions
                          (filter #(= (:type %) "question"))
                          (take-last 3)
                          (map :content)
                          (map #(str/join " " (take 5 (str/split % #"\s+")))))] ; First 5 words of each question
    (if (empty? interactions)
      "No previous interactions"
      (str "Recent Q&A session with " question-count " questions. "
           "Recent topics: " (str/join ", " recent-topics)))))

(defn update-timeline-summary [session-id summary]
  "Update the summary field in timeline"
  (let [timeline (load-timeline session-id)
        updated-timeline (-> timeline
                           (assoc :summary summary)
                           (assoc :last_updated (current-timestamp)))]
    (save-timeline session-id updated-timeline)
    updated-timeline))

(defn get-timeline-context [session-id]
  "Get timeline-based context for session (for default session)"
  (let [timeline (load-timeline session-id)]
    (if (empty? (:interactions timeline))
      "General Amazon Q assistance - Timeline summary will be generated from interactions"
      (or (:summary timeline)
          (generate-timeline-summary session-id)))))
