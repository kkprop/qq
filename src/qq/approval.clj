(ns qq.approval
  "Intelligent approval system for Q tool usage with safety analysis"
  (:require [clojure.string :as str]))

;; Safety analysis patterns
(def dangerous-patterns
  "Patterns that indicate potentially dangerous operations"
  [;; File system dangers
   #"rm\s+-rf"
   #"sudo\s+rm"
   #"chmod\s+777"
   #"chown\s+root"
   #">/dev/null"
   
   ;; Network dangers
   #"curl.*\|\s*sh"
   #"wget.*\|\s*sh"
   #"nc\s+-l"
   #"netcat.*-l"
   
   ;; System dangers
   #"sudo\s+"
   #"su\s+-"
   #"passwd"
   #"useradd"
   #"userdel"
   #"systemctl"
   #"service\s+"
   
   ;; Process dangers
   #"kill\s+-9"
   #"killall"
   #"pkill"
   
   ;; Data dangers
   #"dd\s+if="
   #"mkfs"
   #"fdisk"
   #"parted"
   
   ;; Remote execution
   #"ssh.*;"
   #"scp.*;"
   #"rsync.*--delete"])

(def safe-patterns
  "Patterns that are generally safe operations"
  [;; Read-only operations
   #"^ls\s+"
   #"^find\s+.*-type\s+f"
   #"^cat\s+"
   #"^head\s+"
   #"^tail\s+"
   #"^grep\s+"
   #"^wc\s+"
   #"^sort\s+"
   #"^uniq\s+"
   
   ;; Safe analysis
   #"^file\s+"
   #"^stat\s+"
   #"^du\s+"
   #"^df\s+"
   #"^ps\s+"
   #"^top\s+"
   #"^htop\s+"
   
   ;; Git operations (mostly safe)
   #"^git\s+log"
   #"^git\s+status"
   #"^git\s+diff"
   #"^git\s+show"
   #"^git\s+branch"
   
   ;; Package info (read-only)
   #"^npm\s+list"
   #"^pip\s+list"
   #"^brew\s+list"])

(defn analyze-command-safety [command]
  "Analyze if a command is safe to execute automatically"
  (let [cmd-lower (str/lower-case command)]
    (cond
      ;; Check for explicitly dangerous patterns
      (some #(re-find % cmd-lower) dangerous-patterns)
      {:safe false 
       :reason "Contains dangerous patterns"
       :recommendation "Manual approval required"}
      
      ;; Check for explicitly safe patterns
      (some #(re-find % cmd-lower) safe-patterns)
      {:safe true 
       :reason "Matches safe operation patterns"
       :recommendation "Auto-approve"}
      
      ;; Default: be cautious
      :else
      {:safe false 
       :reason "Unknown operation - err on side of caution"
       :recommendation "Manual approval required"})))

(defn analyze-tool-safety [tool-name tool-params]
  "Analyze if a tool usage is safe based on tool type and parameters"
  (case tool-name
    ;; File system tools
    "fs_read" {:safe true :reason "Read-only file system access"}
    "fs_write" {:safe false :reason "File modification - requires approval"}
    
    ;; Execution tools
    "execute_bash" (if-let [command (:command tool-params)]
                     (analyze-command-safety command)
                     {:safe false :reason "No command specified"})
    
    ;; Network tools
    "exa___web_search_exa" {:safe true :reason "Web search is read-only"}
    "exa___crawling_exa" {:safe true :reason "Web crawling is read-only"}
    
    ;; System tools
    "applescript_mcp___system_launch_app" {:safe false :reason "System modification"}
    "applescript_mcp___system_quit_app" {:safe false :reason "System modification"}
    
    ;; Default: unknown tool
    {:safe false 
     :reason (str "Unknown tool: " tool-name)
     :recommendation "Manual approval required"}))

(defn extract-tool-info [permission-text]
  "Extract tool information from Q's permission request"
  (let [lines (str/split-lines permission-text)]
    (loop [remaining lines
           tool-name nil
           command nil]
      (if (empty? remaining)
        {:tool-name tool-name :command command}
        (let [line (first remaining)]
          (cond
            ;; Tool usage line
            (str/includes? line "Using tool:")
            (let [tool (second (str/split line #"Using tool:\s*"))]
              (recur (rest remaining) (str/trim tool) command))
            
            ;; Command line
            (and (str/includes? line "shell command:")
                 (not command))
            (let [cmd-line (second (str/split line #"shell command:\s*"))]
              (recur (rest remaining) tool-name (str/trim cmd-line)))
            
            ;; Continue searching
            :else
            (recur (rest remaining) tool-name command)))))))

(defn should-auto-approve? [permission-text]
  "Determine if a permission request should be auto-approved based on safety analysis"
  (let [tool-info (extract-tool-info permission-text)
        tool-name (:tool-name tool-info)
        command (:command tool-info)]
    
    (if tool-name
      (let [safety-analysis (analyze-tool-safety tool-name {:command command})]
        (println (str "🔍 Safety analysis for " tool-name ": " (:reason safety-analysis)))
        
        (if (:safe safety-analysis)
          {:approve true 
           :reason (:reason safety-analysis)
           :response "y"}
          {:approve false 
           :reason (:reason safety-analysis)
           :response "manual"}))
      
      ;; No tool info found - be cautious
      {:approve false 
       :reason "Could not parse tool information"
       :response "manual"})))

(defn get-approval-response [permission-text]
  "Get the appropriate response for a permission request"
  (let [approval-decision (should-auto-approve? permission-text)]
    (if (:approve approval-decision)
      (do
        (println (str "✅ Auto-approving: " (:reason approval-decision)))
        "y")
      (do
        (println (str "⚠️  Manual approval required: " (:reason approval-decision)))
        (println "🔒 Skipping auto-approval for safety")
        nil))))  ; Return nil to indicate manual approval needed
