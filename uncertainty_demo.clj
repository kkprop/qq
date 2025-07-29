(ns uncertainty-demo
  "Demonstration of uncertainty handling in AI approval systems")

;; Example uncertainty handling strategies
(defn analyze-uncertainty [command]
  "Demonstrate different approaches to handling uncertain commands"
  
  (println (str "🔍 Analyzing command: " command))
  
  ;; Strategy 1: Context-based confidence
  (let [context {:length (count command)
                 :has-network (re-find #"curl|wget|ssh" command)
                 :has-sudo (re-find #"sudo" command)
                 :has-wildcards (re-find #"\*|\?" command)
                 :reads-system (re-find #"/etc/|/var/|passwd" command)}]
    
    (println "📊 Context analysis:")
    (doseq [[key value] context]
      (when value
        (println (str "  ⚠️  " (name key) ": " value))))
    
    ;; Strategy 2: Confidence-based decision
    (cond
      ;; High confidence dangerous
      (:has-sudo context)
      {:decision :block
       :confidence :high
       :reason "Contains sudo - always dangerous"}
      
      ;; Medium confidence risky
      (or (:has-network context) (:reads-system context))
      {:decision :request-approval
       :confidence :medium
       :reason "Network/system access - needs review"}
      
      ;; Low confidence - simple command
      (and (< (:length context) 30) 
           (not (:has-wildcards context)))
      {:decision :approve-with-logging
       :confidence :medium
       :reason "Simple command - likely safe"}
      
      ;; Unknown
      :else
      {:decision :request-with-context
       :confidence :low
       :reason "Unknown pattern - need user guidance"})))

;; Demonstration
(defn demo-uncertainty-handling []
  "Demonstrate uncertainty handling with various commands"
  
  (println "🎯 UNCERTAINTY HANDLING DEMONSTRATION")
  (println "=====================================")
  
  (let [test-commands ["which python3"
                       "curl -s https://api.github.com/user" 
                       "sudo rm -rf /tmp/test"
                       "ls -la | grep backup"
                       "find . -name '*.clj' -exec rm {} \\;"]]
    
    (doseq [cmd test-commands]
      (println)
      (let [result (analyze-uncertainty cmd)]
        (println (str "📋 Decision: " (:decision result)))
        (println (str "🎯 Confidence: " (:confidence result)))
        (println (str "💭 Reason: " (:reason result)))
        
        ;; Show what action would be taken
        (case (:decision result)
          :block 
          (println "🚫 ACTION: Block execution")
          
          :approve-with-logging
          (println "✅ ACTION: Auto-approve with logging")
          
          :request-approval
          (println "❓ ACTION: Request user approval")
          
          :request-with-context
          (println "❓ ACTION: Request approval + suggest pattern addition"))
        
        (println "---")))))

;; Run the demonstration
(demo-uncertainty-handling)
