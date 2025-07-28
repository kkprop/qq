(ns qq.session
  "Session management and storage"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

;; Configuration
(def ^:private QQ-DIR (str (System/getProperty "user.home") "/.knock/qq"))
(def ^:private SESSIONS-FILE (str QQ-DIR "/sessions.json"))
(def ^:private SESSIONS-DIR (str QQ-DIR "/sessions"))

;; Utility Functions

(defn- ensure-qq-dir []
  "Ensure QQ directory structure exists"
  (.mkdirs (io/file QQ-DIR))
  (.mkdirs (io/file SESSIONS-DIR)))

(defn generate-id []
  "Generate a unique session ID"
  (str (java.util.UUID/randomUUID)))

(defn- load-sessions-registry []
  "Load sessions registry from disk"
  (ensure-qq-dir)
  (if (.exists (io/file SESSIONS-FILE))
    (try
      (json/read-str (slurp SESSIONS-FILE) :key-fn keyword)
      (catch Exception e
        (println "Warning: Could not load sessions registry:" (.getMessage e))
        {}))
    {}))

(defn- save-sessions-registry [registry]
  "Save sessions registry to disk"
  (ensure-qq-dir)
  (try
    (spit SESSIONS-FILE (json/write-str registry {:pretty true}))
    (catch Exception e
      (println "Warning: Could not save sessions registry:" (.getMessage e)))))

;; Public API

(defn save [session-data]
  "Save session metadata"
  (ensure-qq-dir)
  (let [session-id (:id session-data)
        session-dir (str SESSIONS-DIR "/" session-id)
        metadata-file (str session-dir "/metadata.json")]
    
    ;; Create session directory
    (.mkdirs (io/file session-dir))
    
    ;; Save metadata
    (spit metadata-file (json/write-str session-data {:pretty true}))
    
    ;; Update registry
    (let [registry (load-sessions-registry)
          updated-registry (assoc registry (keyword session-id) 
                                  {:name (:name session-data)
                                   :created-at (:created-at session-data)
                                   :last-activity (:last-activity session-data)})]
      (save-sessions-registry updated-registry))))

(defn load [session-id]
  "Load session metadata"
  (let [metadata-file (str SESSIONS-DIR "/" session-id "/metadata.json")]
    (if (.exists (io/file metadata-file))
      (try
        (json/read-str (slurp metadata-file) :key-fn keyword)
        (catch Exception e
          (println "Warning: Could not load session metadata:" (.getMessage e))
          nil))
      nil)))

(defn ensure-default-session []
  "Ensure default session exists, create if needed"
  (let [default-session-id "default"]
    (if-let [existing-session (load default-session-id)]
      ;; Default session exists, return its ID
      default-session-id
      ;; Create new default session
      (let [default-session {:id default-session-id
                            :name "default"
                            :context "General Amazon Q assistance - Timeline summary will be generated from interactions"
                            :created-at (System/currentTimeMillis)
                            :last-activity (System/currentTimeMillis)
                            :message-count 0
                            :is-default true}]
        (println "🚀 Creating default Q session...")
        ;; Save session metadata
        (save default-session)
        (println "✅ Default session created")
        default-session-id))))

(defn is-default-session? [session-id]
  "Check if session is the default session"
  (= session-id "default"))

(defn get-default-session []
  "Get default session, ensuring it exists"
  (let [default-id (ensure-default-session)]
    (load default-id)))

(defn list-all []
  "List all sessions, with default session marked"
  (let [registry (load-sessions-registry)]
    (->> registry
         (map (fn [[id data]]
                (let [full-data (load (name id))
                      is-default (is-default-session? (name id))]
                  (assoc (merge data full-data) :is-default is-default))))
         (sort-by (fn [session] 
                   ;; Sort default first, then by last activity
                   [(not (:is-default session)) (- (:last-activity session))]))
         vec)))

(defn resolve-name [session-name-or-id]
  "Resolve session name to ID, with fuzzy matching"
  (let [sessions (list-all)
        exact-match (first (filter #(= (:name %) session-name-or-id) sessions))
        id-match (first (filter #(= (:id %) session-name-or-id) sessions))]
    
    (cond
      exact-match (:id exact-match)
      id-match (:id id-match)
      :else
      ;; Fuzzy matching
      (let [fuzzy-matches (filter #(str/includes? (:name %) session-name-or-id) sessions)]
        (case (count fuzzy-matches)
          0 nil
          1 (:id (first fuzzy-matches))
          ;; Multiple matches - let user choose
          (do
            (println "Multiple sessions match. Please specify:")
            (doseq [[i session] (map-indexed vector fuzzy-matches)]
              (println (str "  " (inc i) ". " (:name session))))
            nil))))))

(defn update-activity [session-id]
  "Update last activity timestamp for session"
  (when-let [session (load session-id)]
    (let [updated-session (assoc session 
                                :last-activity (System/currentTimeMillis)
                                :message-count (inc (:message-count session 0)))]
      (save updated-session))))

(defn format-age [timestamp]
  "Format timestamp as human-readable age"
  (let [now (System/currentTimeMillis)
        diff-ms (- now timestamp)
        diff-minutes (/ diff-ms 1000 60)
        diff-hours (/ diff-minutes 60)
        diff-days (/ diff-hours 24)]
    (cond
      (< diff-minutes 1) "just now"
      (< diff-minutes 60) (str (int diff-minutes) "m ago")
      (< diff-hours 24) (str (int diff-hours) "h ago")
      :else (str (int diff-days) "d ago"))))

(defn truncate-context [context max-length]
  "Truncate context string for display"
  (if (> (count context) max-length)
    (str (subs context 0 (- max-length 3)) "...")
    context))
