(ns qq.session.manager
  "Real Q Session Management - Actually creates and manages Q sessions"
  (:require [babashka.process :as p]
            [clojure.data.json :as json]
            [clojure.string :as str]))

;; Real session management functions
(defn get-active-sessions
  "Get actual active Q sessions from the system"
  []
  (try
    ;; This would check for actual Q sessions
    ;; For now, let's check tmux sessions that start with 'qq-'
    (let [result (p/shell {:out :string} "tmux list-sessions")
          sessions (str/split-lines (:out result))
          qq-sessions (filter #(str/includes? % "qq-") sessions)]
      (mapv (fn [session-line]
              (let [parts (str/split session-line #":")
                    name (first parts)
                    status (if (str/includes? session-line "attached") "active" "idle")]
                {:name name
                 :status status
                 :messages 0  ; Would need to count actual messages
                 :created (java.time.Instant/now)}))
            qq-sessions))
    (catch Exception e
      (println "Error getting sessions:" (.getMessage e))
      [])))

(defn create-q-session
  "Actually create a new Q session using QQ CLI"
  [session-name]
  (let [clean-name (or session-name (str "qq-session-" (System/currentTimeMillis)))
        tmux-name (str "qq-" clean-name)]
    
    (println (str "🚀 Creating real Q session: " tmux-name))
    
    (try
      ;; Create tmux session for the Q chat
      (p/shell "tmux" "new-session" "-d" "-s" tmux-name "-c" (System/getProperty "user.dir"))
      
      ;; Start Q chat in the session
      (p/shell "tmux" "send-keys" "-t" tmux-name "q chat" "Enter")
      
      (println (str "✅ Created Q session: " tmux-name))
      
      {:success true
       :session {:name tmux-name
                 :status "active"
                 :messages 0
                 :created (java.time.Instant/now)}}
      
      (catch Exception e
        (println (str "❌ Error creating session: " (.getMessage e)))
        {:success false
         :error (.getMessage e)}))))

(defn kill-q-session
  "Kill a Q session"
  [session-name]
  (try
    (p/shell "tmux" "kill-session" "-t" session-name)
    {:success true}
    (catch Exception e
      {:success false
       :error (.getMessage e)})))

(defn attach-to-session
  "Get command to attach to a Q session"
  [session-name]
  (str "tmux attach -t " session-name))

(defn get-session-info
  "Get detailed info about a specific session"
  [session-name]
  (try
    (let [result (p/shell {:out :string} "tmux" "list-sessions" "-F" "#{session_name}:#{session_attached}:#{session_created}")
          lines (str/split-lines (:out result))
          session-line (first (filter #(str/starts-with? % session-name) lines))]
      
      (if session-line
        (let [parts (str/split session-line #":")
              attached? (= "1" (second parts))
              created (nth parts 2 "unknown")]
          {:name session-name
           :status (if attached? "active" "idle")
           :attached attached?
           :created created
           :messages 0})  ; Would need to count actual messages
        nil))
    
    (catch Exception e
      (println (str "Error getting session info: " (.getMessage e)))
      nil)))

;; Web API integration
(defn sessions-api-handler
  "Handle /api/sessions requests with real data"
  []
  (let [sessions (get-active-sessions)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str sessions)}))

(defn create-session-api-handler
  "Handle POST /api/create-session requests"
  [request]
  (try
    (let [body (json/read-str (slurp (:body request)) :key-fn keyword)
          session-name (:name body)
          result (create-q-session session-name)]
      
      {:status (if (:success result) 200 500)
       :headers {"Content-Type" "application/json"}
       :body (json/write-str result)})
    
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:success false
                              :error (.getMessage e)})})))

(defn kill-session-api-handler
  "Handle DELETE /api/sessions/:name requests"
  [session-name]
  (let [result (kill-q-session session-name)]
    {:status (if (:success result) 200 500)
     :headers {"Content-Type" "application/json"}
     :body (json/write-str result)}))
