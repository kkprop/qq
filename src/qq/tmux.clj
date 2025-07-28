(ns qq.tmux
  "Tmux integration with incremental output caching"
  (:require [clojure.string :as str]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [qq.timeline :as timeline]))

;; Configuration
(def ^:private QQ-DIR (str (System/getProperty "user.home") "/.knock/qq"))
(def ^:private SESSION-PREFIX "qq-")

;; Utility Functions

(defn session-name [terse-name]
  "Generate tmux session name from terse name"
  (str SESSION-PREFIX terse-name))

(defn- session-log-file [session-id]
  "Get log file path for session"
  (str QQ-DIR "/sessions/" session-id "/tmux.log"))

(defn- ensure-session-dir [session-id]
  "Ensure session directory exists"
  (let [session-dir (str QQ-DIR "/sessions/" session-id)]
    (.mkdirs (io/file session-dir))))

;; Tmux Operations

(defn create-session [session-id]
  "Create a new tmux session for Amazon Q"
  (ensure-session-dir session-id)
  (let [tmux-name (session-name session-id)]
    (try
      ;; Create detached tmux session
      (p/shell "tmux" "new-session" "-d" "-s" tmux-name)
      ;; Start Amazon Q in the session
      (p/shell "tmux" "send-keys" "-t" tmux-name "q chat" "Enter")
      ;; Wait a moment for Q to start
      (Thread/sleep 2000)
      {:success true :tmux-name tmux-name}
      (catch Exception e
        (println "Error creating tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))

(defn capture-output [session-id]
  "Capture current tmux session output"
  (let [tmux-name (session-name session-id)]
    (try
      (let [result (p/shell {:out :string :continue true} 
                           "tmux" "capture-pane" "-t" tmux-name "-p")]
        (if (zero? (:exit result))
          (:out result)
          (do
            (println "Warning: Could not capture tmux output for session" session-id)
            "")))
      (catch Exception e
        (println "Error capturing tmux output:" (.getMessage e))
        ""))))

(defn send-keys [session-id text]
  "Send keys to tmux session"
  (let [tmux-name (session-name session-id)]
    (try
      (p/shell "tmux" "send-keys" "-t" tmux-name text "Enter")
      {:success true}
      (catch Exception e
        (println "Error sending keys to tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))

(defn- detect-error-output [output]
  "Detect if output contains Q errors"
  (or (str/includes? output "\u001b[31m")  ; Red ANSI color
      (str/includes? output "Error:")
      (str/includes? output "ERROR:")
      (str/includes? output "Failed:")))

(defn- auto-continue-on-error [session-id]
  "Send 'continue' command if Q is in error state"
  (println "🔄 Detected Q error, sending 'continue' to recover...")
  (send-keys session-id "continue")
  (Thread/sleep 1000))

(defn send-and-wait [session-id question]
  "Send question and wait for response (synchronous) with timeline logging"
  (let [tmux-name (session-name session-id)
        start-time (System/currentTimeMillis)]
    ;; Log the question to timeline
    (timeline/log-question session-id question)
    
    ;; Capture initial state
    (let [initial-output (capture-output session-id)]
      ;; Send question
      (send-keys session-id question)
      
      ;; Wait for response with polling
      (loop [attempts 0
             max-attempts 60]  ; 5 minutes timeout
        (Thread/sleep 5000)  ; Wait 5 seconds between checks
        (let [current-output (capture-output session-id)
              new-content (if (str/includes? current-output initial-output)
                           (str/replace-first current-output initial-output "")
                           current-output)]
          
          (cond
            ;; Got new content
            (and (not (str/blank? new-content))
                 (not= new-content initial-output))
            (let [response-time (- (System/currentTimeMillis) start-time)
                  final-response (if (detect-error-output new-content)
                                  (do
                                    (auto-continue-on-error session-id)
                                    ;; Wait for recovery and get new output
                                    (Thread/sleep 3000)
                                    (let [recovered-output (capture-output session-id)]
                                      (if (detect-error-output recovered-output)
                                        new-content  ; Return error if recovery failed
                                        recovered-output)))  ; Return recovered output
                                  new-content)]
              ;; Log the answer to timeline
              (timeline/log-answer session-id final-response response-time)
              final-response)
            
            ;; Timeout
            (>= attempts max-attempts)
            (let [timeout-response "⏰ Timeout: No response from Amazon Q"
                  response-time (- (System/currentTimeMillis) start-time)]
              (println "⏰ Timeout waiting for Q response")
              ;; Log timeout as answer
              (timeline/log-answer session-id timeout-response response-time)
              timeout-response)
            
            ;; Continue waiting
            :else
            (recur (inc attempts) max-attempts)))))))

;; Enhanced Async Output with Progress Updates

;; Advanced Q Timeline Parser for Multi-Round Conversations

(defn- detect-q-state [output]
  "Detect Q's current state from tmux output"
  (let [lines (str/split-lines output)
        last-few-lines (take-last 5 lines)
        last-line (str/trim (last lines))]
    (cond
      ;; Q is asking for permission
      (some #(str/includes? % "Allow this action?") last-few-lines)
      {:state :waiting-permission :context "tool-permission"}
      
      ;; Q is waiting for user input (ends with > and cursor)
      (and (= last-line ">") 
           (not (some #(str/includes? % "🛠️") last-few-lines)))
      {:state :complete :context "response-finished"}
      
      ;; Q is using tools (shows tool markers)
      (some #(str/includes? % "🛠️") last-few-lines)
      {:state :using-tools :context "tool-execution"}
      
      ;; Q is thinking or processing
      (some #(str/includes? % "⠹ Thinking...") last-few-lines)
      {:state :thinking :context "processing"}
      
      ;; Q is in middle of response (has content but no final >)
      (and (not= last-line ">")
           (some #(str/starts-with? (str/trim %) ">") last-few-lines))
      {:state :responding :context "multi-segment-response"}
      
      ;; Default: unknown state
      :else
      {:state :unknown :context "unclear"})))

(defn- extract-multi-segment-response [output question]
  "Extract complete multi-segment response including tool usage"
  (let [lines (str/split-lines output)
        ;; Find the question line
        question-idx (last (keep-indexed 
                           (fn [idx line] 
                             (when (and (str/starts-with? (str/trim line) ">")
                                       (str/includes? line question)) 
                               idx)) 
                           lines))]
    (if question-idx
      ;; Extract everything from question until final standalone >
      (let [response-lines (drop (inc question-idx) lines)
            ;; Take until we hit a standalone > that's not part of content
            complete-response (loop [remaining response-lines
                                   collected []
                                   in-tool-section false]
                               (if (empty? remaining)
                                 collected
                                 (let [line (first remaining)
                                       trimmed (str/trim line)]
                                   (cond
                                     ;; Final standalone > - we're done
                                     (and (= trimmed ">") 
                                          (not in-tool-section)
                                          (not (str/includes? (str/join "\n" (take 3 (rest remaining))) "🛠️")))
                                     collected
                                     
                                     ;; Tool section marker
                                     (str/includes? line "🛠️")
                                     (recur (rest remaining) (conj collected line) true)
                                     
                                     ;; Tool completion
                                     (str/includes? line "● Completed in")
                                     (recur (rest remaining) (conj collected line) false)
                                     
                                     ;; Regular content line
                                     :else
                                     (recur (rest remaining) (conj collected line) in-tool-section)))))]
        (str/trim (str/join "\n" complete-response)))
      "")))

(def auto-responses
  "Auto-responses for common Q permission requests"
  {"Allow this action? Use 't' to trust (always allow) this tool for the session. [y/n/t]:" "y"
   "Allow this action? [y/n]:" "y"
   "Continue with this operation? [y/n]:" "y"
   "Proceed? [y/n]:" "y"})

(defn- handle-auto-response [session-id output last-output]
  "Handle automatic responses to Q's permission requests (only if new)"
  (when (not= output last-output)  ; Only check if output changed
    (let [lines (str/split-lines output)
          last-few-lines (take-last 3 lines)]
      (doseq [line last-few-lines]  ; Only check recent lines
        (doseq [[pattern response] auto-responses]
          (when (and (str/includes? line pattern)
                     (not (str/includes? (or last-output "") pattern)))  ; Not in previous output
            (println (str "🤖 Auto-responding to Q permission: " response))
            (send-keys session-id response)
            (Thread/sleep 1000))))))  ; Longer delay

(defn send-and-wait-advanced [session-id question]
  "Advanced Q conversation handler for multi-round responses with tool usage"
  (let [start-time (System/currentTimeMillis)]
    ;; Log the question to timeline
    (timeline/log-question session-id question)
    
    ;; Send question
    (send-keys session-id question)
    
    ;; Advanced polling with state detection
    (loop [attempts 0
           max-attempts 60  ; 2 minutes max for complex operations
           last-state nil
           last-output ""]
      (Thread/sleep 3000)  ; Check every 3 seconds for complex operations
      (let [current-output (capture-output session-id)
            current-state (detect-q-state current-output)]
        
        (cond
          ;; Q is asking for permission - auto-respond
          (= (:state current-state) :waiting-permission)
          (do
            (handle-auto-response session-id current-output last-output)
            (recur (inc attempts) max-attempts current-state current-output))
          
          ;; Q has completed the full response
          (= (:state current-state) :complete)
          (let [response (extract-multi-segment-response current-output question)
                response-time (- (System/currentTimeMillis) start-time)]
            (println (str "✅ Multi-round Q conversation completed in " (/ response-time 1000.0) "s"))
            (timeline/log-answer session-id response response-time)
            response)
          
          ;; Q is still working (using tools, thinking, responding)
          (#{:using-tools :thinking :responding} (:state current-state))
          (do
            (when (not= current-state last-state)
              (println (str "🔄 Q state: " (:state current-state) " - " (:context current-state))))
            (recur (inc attempts) max-attempts current-state current-output))
          
          ;; Timeout
          (>= attempts max-attempts)
          (let [timeout-response (str "⏰ Timeout: Q conversation exceeded " max-attempts " attempts")
                response-time (- (System/currentTimeMillis) start-time)]
            (println timeout-response)
            (timeline/log-answer session-id timeout-response response-time)
            timeout-response)
          
          ;; Unknown state - continue waiting but log it
          :else
          (do
            (when (not= current-state last-state)
              (println (str "❓ Unknown Q state: " current-state)))
            (recur (inc attempts) max-attempts current-state current-output)))))))

;; Update the async function to use advanced parsing
(defn send-async-advanced [session-id question]
  "Send question asynchronously using advanced multi-round conversation handling"
  (let [promise (promise)]
    (future
      (try
        (let [response (send-and-wait-advanced session-id question)]
          (deliver promise {:success true :response response}))
        (catch Exception e
          (println (str "❌ Error in advanced async question: " (.getMessage e)))
          (deliver promise {:success false :error (.getMessage e)}))))
    promise))

;; Legacy functions for backward compatibility
(defn- extract-latest-response [tmux-output question]
  "Extract the response to the most recent question based on actual Q timeline structure"
  (let [lines (str/split-lines tmux-output)
        ;; Find the last occurrence of our question (starts with > and contains question)
        question-idx (last (keep-indexed 
                           (fn [idx line] 
                             (when (and (str/starts-with? (str/trim line) ">")
                                       (str/includes? line question)) 
                               idx)) 
                           lines))]
    (if question-idx
      ;; Extract response: from line after question until standalone ">"
      (let [response-start (inc question-idx)
            response-lines (take-while 
                           #(not (= (str/trim %) ">"))
                           (drop response-start lines))]
        (str/trim (str/join "\n" response-lines)))
      "")))

(defn send-and-wait-improved [session-id question]
  "Send question and wait for response using improved Q timeline understanding"
  (let [start-time (System/currentTimeMillis)]
    ;; Log the question to timeline
    (timeline/log-question session-id question)
    
    ;; Send question
    (send-keys session-id question)
    
    ;; Wait for response with simple polling based on actual Q behavior
    (loop [attempts 0
           max-attempts 30]  ; 30 attempts = 60 seconds max
      (Thread/sleep 2000)  ; Check every 2 seconds
      (let [current-output (capture-output session-id)
            response (extract-latest-response current-output question)]
        
        (cond
          ;; Got a complete response (non-empty and ends with standalone >)
          (and (not (str/blank? response))
               (str/ends-with? current-output "\n>\n"))
          (let [response-time (- (System/currentTimeMillis) start-time)]
            (timeline/log-answer session-id response response-time)
            response)
          
          ;; Timeout
          (>= attempts max-attempts)
          (let [timeout-response "⏰ Timeout: No response from Amazon Q"
                response-time (- (System/currentTimeMillis) start-time)]
            (println "⏰ Timeout waiting for Q response")
            (timeline/log-answer session-id timeout-response response-time)
            timeout-response)
          
          ;; Continue waiting
          :else
          (recur (inc attempts) max-attempts))))))

(defn- extract-current-response [current-output question]
  "Extract just the response to the current question from tmux output"
  (let [lines (str/split-lines current-output)
        ;; Find the line with our question
        question-idx (first (keep-indexed 
                            (fn [idx line] 
                              (when (str/includes? line question) idx)) 
                            lines))]
    (if question-idx
      ;; Get lines after the question until we hit a standalone ">" at the end
      (let [after-question (drop (inc question-idx) lines)
            ;; Take all lines until we find a line that is just ">" (the final prompt)
            response-lines (loop [remaining after-question
                                 collected []]
                            (if (empty? remaining)
                              collected
                              (let [line (first remaining)]
                                (if (= (str/trim line) ">")
                                  collected  ; Stop when we hit the final prompt
                                  (recur (rest remaining) (conj collected line))))))]
        (str/trim (str/join "\n" response-lines)))
      ;; Fallback: return empty if we can't find the question
      "")))

(defn send-with-progress [session-id question progress-callback]
  "Send question with progress updates using improved polling"
  (let [tmux-name (session-name session-id)
        start-time (System/currentTimeMillis)]
    ;; Log the question to timeline
    (timeline/log-question session-id question)
    
    ;; Capture initial state
    (let [initial-output (capture-output session-id)]
      ;; Send question
      (send-keys session-id question)
      
      ;; Wait for response with progress updates
      (loop [attempts 0
             max-attempts 60
             last-output initial-output]
        (Thread/sleep 2000)  ; Check every 2 seconds (faster than original)
        (let [current-output (capture-output session-id)
              new-content (if (str/includes? current-output last-output)
                           (str/replace-first current-output last-output "")
                           current-output)]
          
          (cond
            ;; Got new content - call progress callback and continue
            (and (not (str/blank? new-content))
                 (not= current-output last-output))
            (do
              (progress-callback new-content current-output)
              ;; Check if response seems complete - look for actual response end patterns
              (if (and (str/includes? current-output "\n>")
                       ;; And no loading indicators
                       (not (str/includes? current-output "⠼ Thinking..."))
                       (not (str/includes? current-output "⠋"))
                       (not (str/includes? current-output "⠙"))
                       (not (str/includes? current-output "⠹"))
                       (not (str/includes? current-output "⠸"))
                       (not (str/includes? current-output "⠼"))
                       (not (str/includes? current-output "⠴"))
                       (not (str/includes? current-output "⠦"))
                       (not (str/includes? current-output "⠧"))
                       (not (str/includes? current-output "⠇"))
                       (not (str/includes? current-output "⠏")))
                ;; Extract just the current response
                (let [response-time (- (System/currentTimeMillis) start-time)
                      final-response (extract-current-response current-output question)]
                  (timeline/log-answer session-id final-response response-time)
                  final-response)
                (recur (inc attempts) max-attempts current-output)))
            
            ;; Timeout
            (>= attempts max-attempts)
            (let [timeout-response "⏰ Timeout: No response from Amazon Q"
                  response-time (- (System/currentTimeMillis) start-time)]
              (println "⏰ Timeout waiting for Q response")
              (timeline/log-answer session-id timeout-response response-time)
              timeout-response)
            
            ;; Continue waiting
            :else
            (recur (inc attempts) max-attempts last-output)))))))

(defn send-async [session-id question]
  "Send question asynchronously using advanced multi-round conversation handling"
  (let [promise (promise)]
    (future
      (try
        ;; Use the advanced parser for multi-round conversations
        (let [response (send-and-wait-advanced session-id question)]
          (deliver promise {:success true :response response}))
        (catch Exception e
          (println (str "❌ Error in async question: " (.getMessage e)))
          (deliver promise {:success false :error (.getMessage e)}))))
    promise))

(defn send-async-with-progress [session-id question progress-callback]
  "Send question asynchronously with progress updates"
  (let [promise (promise)]
    (future
      (try
        (let [response (send-with-progress session-id question progress-callback)]
          (deliver promise {:success true :response response}))
        (catch Exception e
          (println (str "❌ Error in async progress question: " (.getMessage e)))
          (deliver promise {:success false :error (.getMessage e)}))))
    promise))

(defn session-exists? [session-id]
  "Check if tmux session exists"
  (let [tmux-name (session-name session-id)]
    (try
      (let [result (p/shell {:continue true} "tmux" "has-session" "-t" tmux-name)]
        (zero? (:exit result)))
      (catch Exception e
        false))))

(defn kill-session [session-id]
  "Kill tmux session"
  (let [tmux-name (session-name session-id)]
    (try
      (p/shell "tmux" "kill-session" "-t" tmux-name)
      {:success true}
      (catch Exception e
        (println "Error killing tmux session:" (.getMessage e))
        {:success false :error (.getMessage e)}))))
