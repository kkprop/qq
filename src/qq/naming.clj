(ns qq.naming
  "Dedicated Q window for generating terse session names"
  (:require [clojure.string :as str]
            [babashka.process :as p]
            [qq.tmux :as tmux]))

;; Configuration
(def ^:private NAMING-SESSION-ID "naming-service")
(def ^:private NAMING-TMUX-NAME "qq-naming-service")

;; Naming Service Management

(defn- ensure-naming-service []
  "Ensure naming service Q session is running"
  (when-not (tmux/session-exists? NAMING-SESSION-ID)
    (println "🚀 Starting naming service...")
    (tmux/create-session NAMING-SESSION-ID)
    ;; Wait for Q to fully initialize
    (Thread/sleep 5000)
    ;; Send initial context to naming service
    (tmux/send-keys NAMING-SESSION-ID 
                   "You are a naming service. Your job is to create terse, descriptive names for AI conversation sessions. 
                   
Rules:
- Maximum 10 words
- Use hyphens to separate words  
- Be descriptive but concise
- Focus on the main topic/domain
- Use lowercase
- No special characters except hyphens

When I give you a context description, respond with ONLY the terse name, nothing else.

Example:
Context: 'Analyzing AWS Lambda cold start performance issues'
Response: lambda-cold-start-performance-analysis

Ready to generate names.")
    (Thread/sleep 3000)))

(defn generate-name [context]
  "Generate a terse session name from context using naming service"
  (try
    (ensure-naming-service)
    
    ;; Send context to naming service
    (let [prompt (str "Generate a terse name for this session context: " context)]
      (let [response (tmux/send-and-wait NAMING-SESSION-ID prompt)]
        ;; Extract the name from response (should be just the name)
        (let [lines (str/split-lines (str/trim response))
              ;; Get the last non-empty line (the generated name)
              name-line (->> lines
                            (remove str/blank?)
                            last
                            str/trim)]
          
          ;; Validate and clean the name
          (if (and name-line 
                   (not (str/blank? name-line))
                   (< (count (str/split name-line #"\s+")) 11))  ; Max 10 words
            ;; Clean the name: lowercase, replace spaces with hyphens, remove special chars
            (-> name-line
                str/lower-case
                (str/replace #"\s+" "-")
                (str/replace #"[^a-z0-9\-]" "")
                (str/replace #"-+" "-")  ; Collapse multiple hyphens
                (str/replace #"^-|-$" ""))  ; Remove leading/trailing hyphens
            
            ;; Fallback name if generation failed
            (str "session-" (System/currentTimeMillis))))))
    
    (catch Exception e
      (println "Warning: Naming service failed, using fallback name:" (.getMessage e))
      (str "session-" (System/currentTimeMillis)))))

(defn restart-naming-service []
  "Restart the naming service (useful for debugging)"
  (when (tmux/session-exists? NAMING-SESSION-ID)
    (tmux/kill-session NAMING-SESSION-ID))
  (Thread/sleep 1000)
  (ensure-naming-service))
