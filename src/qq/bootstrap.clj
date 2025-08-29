(ns qq.bootstrap
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.process :as p]))

(defn bb-edn-exists? 
  "Check if bb.edn exists in current directory"
  []
  (.exists (io/file "bb.edn")))

(defn get-bb-tasks
  "Get list of bb tasks from current directory"
  []
  (when (bb-edn-exists?)
    (try
      (let [result (p/shell {:out :string :err :string} "bb" "tasks")]
        (when (zero? (:exit result))
          (->> (:out result)
               str/split-lines
               (filter #(re-matches #"^[a-z][a-z0-9-]*\s+.*" %))
               (map #(first (str/split % #"\s+")))
               (remove empty?))))
      (catch Exception _ nil))))

(defn generate-bash-completion
  "Generate bash completion script for bb tasks"
  []
  (str "_bb_completion() {\n"
       "    local cur prev tasks\n"
       "    cur=\"${COMP_WORDS[COMP_CWORD]}\"\n"
       "    prev=\"${COMP_WORDS[COMP_CWORD-1]}\"\n"
       "    \n"
       "    if [[ ${prev} == \"bb\" ]]; then\n"
       "        # Only provide completion if bb.edn exists in current directory\n"
       "        if [[ -f \"bb.edn\" ]]; then\n"
       "            # Force fresh read from current directory, no caching\n"
       "            tasks=$(cd \"$PWD\" && bb tasks 2>/dev/null | grep -E \"^[a-z]\" | awk '{print $1}')\n"
       "            COMPREPLY=($(compgen -W \"${tasks}\" -- ${cur}))\n"
       "        else\n"
       "            # Clear completions if no bb.edn\n"
       "            COMPREPLY=()\n"
       "        fi\n"
       "    fi\n"
       "}\n"
       "complete -F _bb_completion bb\n"))

(defn install-bash-completion
  "Install bash completion to user's bashrc"
  []
  (let [bashrc-path (str (System/getProperty "user.home") "/.bashrc")
        completion-script (generate-bash-completion)
        marker "# QQ bb completion"]
    
    (if (.exists (io/file bashrc-path))
      (let [current-content (slurp bashrc-path)]
        (if (str/includes? current-content marker)
          (println "✅ BB completion already installed in ~/.bashrc")
          (do
            (spit bashrc-path 
                  (str current-content "\n\n" marker "\n" completion-script)
                  :append true)
            (println "✅ BB completion installed to ~/.bashrc")
            (println "💡 Run: source ~/.bashrc (or restart terminal)"))))
      (println "❌ ~/.bashrc not found"))))

(defn show-completion-script
  "Display the completion script for manual installation"
  []
  (println "# Add this to your ~/.bashrc:")
  (println)
  (println (generate-bash-completion)))

(defn create-bb-context-hints
  "Create context hints for Amazon Q to discover bb tasks"
  []
  (let [tasks (get-bb-tasks)
        context-file ".bb-context"]
    (when (and tasks (bb-edn-exists?))
      (spit context-file
            (str "# BB Tasks Context for Amazon Q\n"
                 "# Available bb tasks in this project:\n"
                 (str/join "\n" (map #(str "# bb " %) tasks))
                 "\n\n# Common bb usage patterns:\n"
                 "# bb q                    # Direct Q access\n"
                 "# bb create \"context\"     # Create Q session\n"
                 "# bb ask \"question\"      # Ask current session\n"
                 "# bb list                # List all sessions\n"
                 "# bb monitor-resources   # Monitor system resources\n"))
      (println (str "✅ Created " context-file " for Amazon Q context integration")))))

(defn add-bb-to-shell-history
  "Add bb task examples to shell history for Q to learn from"
  []
  (let [tasks (take 10 (get-bb-tasks))
        history-examples (map #(str "bb " %) tasks)]
    (println "💡 To help Amazon Q learn bb tasks, run these commands:")
    (doseq [example history-examples]
      (println (str "  " example " --help")))))

(defn setup-q-integration
  "Setup Amazon Q integration for bb tasks"
  []
  ;; Check if running in Q context (tmux with asciinema) to avoid feedback loops
  (let [in-tmux? (System/getenv "TMUX")
        quiet-mode? (and in-tmux? (System/getenv "ASCIINEMA_REC"))]
    
    (when-not quiet-mode?
      (println "🤖 Setting up Amazon Q integration for bb tasks...")
      (println))
    
    (when (bb-edn-exists?)
      (create-bb-context-hints)
      (when-not quiet-mode?
        (println)
        (println "📋 Context integration setup:")
        (println "  ✅ Created .bb-context file with task descriptions")
        (println "  💡 Amazon Q can now read bb tasks from project context")
        (println)
        (println "🎯 To improve Q's bb task suggestions:")
        (println "  1. Use bb tasks regularly - Q learns from shell history")
        (println "  2. Keep .bb-context file updated")
        (println "  3. Q will gradually learn your bb usage patterns")
        (println)
        (add-bb-to-shell-history)))
    
    (when-not (bb-edn-exists?)
      (when-not quiet-mode?
        (println "❌ No bb.edn found - run this in a babashka project directory")))))

(defn setup-shell-completion
  "Main function to setup shell completion"
  [& args]
  (let [action (first args)]
    (case action
      "install" (install-bash-completion)
      "show" (show-completion-script)
      "q-integration" (setup-q-integration)
      (do
        (println "BB Shell Completion Setup")
        (println "========================")
        (println)
        (if (bb-edn-exists?)
          (do
            (println "✅ bb.edn found in current directory")
            (let [tasks (get-bb-tasks)]
              (println (str "📋 Available tasks: " (count tasks)))
              (println (str "   " (str/join ", " (take 5 tasks)) 
                           (when (> (count tasks) 5) "...")))))
          (println "❌ No bb.edn found in current directory"))
        (println)
        (println "Usage:")
        (println "  bb shell-completion install        # Install bash completion")
        (println "  bb shell-completion show           # Show completion script")
        (println "  bb shell-completion q-integration  # Setup Amazon Q integration")))))
