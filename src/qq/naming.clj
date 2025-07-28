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
        ;; Extract the name from response - look for the generated name line
        ;; First, join all lines and look for patterns after our prompt
        (let [full-text (str/join " " (str/split-lines (str/trim response)))
              ;; Look for the pattern: "> some-name-with-hyphens" after our prompt
              name-pattern (re-find (re-pattern (str "Generate a terse name for this session context: " 
                                                    (java.util.regex.Pattern/quote context) 
                                                    ".*?> ([a-z0-9\\-]+)")) full-text)
              extracted-name (when name-pattern (second name-pattern))]
          
          (if (and extracted-name 
                   (not (str/blank? extracted-name))
                   (str/includes? extracted-name "-")  ; Must contain hyphens
                   (< (count (str/split extracted-name #"-")) 11))  ; Max 10 words
            ;; Clean the name: ensure lowercase, collapse multiple hyphens, remove leading/trailing hyphens
            (-> extracted-name
                str/lower-case
                (str/replace #"-+" "-")  ; Collapse multiple hyphens
                (str/replace #"^-|-$" ""))  ; Remove leading/trailing hyphens
            
            ;; Fallback: try simpler approach - look for any hyphenated word after ">"
            (let [lines (str/split-lines (str/trim response))
                  ;; Find lines that start with ">" and contain hyphens
                  response-lines (->> lines
                                     (filter #(str/starts-with? (str/trim %) ">"))
                                     (map #(str/replace % #"^>\s*" ""))  ; Remove "> " prefix
                                     (map str/trim)
                                     (filter #(and (> (count %) 5)
                                                  (str/includes? % "-")
                                                  (re-matches #"[a-z0-9\-]+" %))))
                  fallback-name (last response-lines)]
              
              (if (and fallback-name (not (str/blank? fallback-name)))
                (-> fallback-name
                    str/lower-case
                    (str/replace #"-+" "-")
                    (str/replace #"^-|-$" ""))
                
                ;; Final fallback
                (do
                  (println "Warning: Could not extract valid name from naming service response")
                  (println "Full response text:" (pr-str (take 200 full-text)))
                  (str "session-" (System/currentTimeMillis)))))))))
    
    (catch Exception e
      (println "Warning: Naming service failed, using fallback name:" (.getMessage e))
      (str "session-" (System/currentTimeMillis)))))

(defn restart-naming-service []
  "Restart the naming service (useful for debugging)"
  (when (tmux/session-exists? NAMING-SESSION-ID)
    (tmux/kill-session NAMING-SESSION-ID))
  (Thread/sleep 1000)
  (ensure-naming-service))
