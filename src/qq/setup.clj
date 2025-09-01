(ns qq.setup
  "Setup global bq alias for qq commands"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn detect-shell []
  "Detect user's shell and return appropriate profile file"
  (let [shell (or (System/getenv "SHELL") "/bin/bash")]
    (cond
      (str/includes? shell "zsh") (str (System/getProperty "user.home") "/.zshrc")
      (str/includes? shell "bash") (str (System/getProperty "user.home") "/.bashrc")
      :else (str (System/getProperty "user.home") "/.profile"))))

(defn get-qq-path []
  "Get the absolute path to qq project directory"
  (System/getProperty "user.dir"))

(defn create-alias-line []
  "Create the bq alias line"
  (str "alias bq='bb --config " (get-qq-path) "/bb.edn'"))

(defn alias-exists? [profile-file alias-line]
  "Check if alias already exists in profile file"
  (try
    (when (.exists (io/file profile-file))
      (let [content (slurp profile-file)]
        (str/includes? content "alias bq=")))
    (catch Exception e false)))

(defn add-alias [profile-file alias-line]
  "Add alias to profile file"
  (try
    (spit profile-file (str "\n# QQ Global Access\n" alias-line "\n") :append true)
    true
    (catch Exception e
      (println "❌ Error writing to" profile-file ":" (.getMessage e))
      false)))

(defn setup-global-alias []
  "Setup global bq alias for qq commands"
  (let [profile-file (detect-shell)
        alias-line (create-alias-line)]
    
    (println "🔧 Setting up global 'bq' alias...")
    (println "📁 QQ Path:" (get-qq-path))
    (println "📝 Profile:" profile-file)
    (println "🔗 Alias:" alias-line)
    
    (if (alias-exists? profile-file alias-line)
      (println "✅ Alias already exists in" profile-file)
      (if (add-alias profile-file alias-line)
        (do
          (println "✅ Alias added to" profile-file)
          (println "🔄 Run 'source" profile-file "' or restart your terminal")
          (println "🚀 Then use 'bq qq' from any directory!"))
        (println "❌ Failed to add alias")))
    
    (println "\n📋 Manual setup (if needed):")
    (println "   echo \"" alias-line "\" >> " profile-file)
    (println "   source " profile-file)))

(defn -main [& args]
  (setup-global-alias))
