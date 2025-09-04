(ns qq.daemon
  "Cross-platform daemon management for QQ watcher"
  (:require [babashka.process :as process]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn get-os-type []
  "Detect operating system type"
  (let [os-name (System/getProperty "os.name")]
    (cond
      (= os-name "Mac OS X") :macos
      (str/includes? os-name "Linux") :linux
      (str/includes? os-name "Windows") :windows
      :else :unknown)))

(defn get-bb-path []
  "Get full path to bb executable"
  (let [result (process/shell {:out :string} "which" "bb")]
    (if (zero? (:exit result))
      (str/trim (:out result))
      "bb"))) ; fallback

(defmulti start-daemon-service 
  "Start watcher daemon service (OS-specific)"
  get-os-type)

(defmethod start-daemon-service :macos [_]
  (println "📡 Starting watcher as macOS launchd service...")
  (process/shell {:dir (System/getProperty "user.dir")} 
                "launchctl" "load" "com.qq.watcher.plist"))

(defmethod start-daemon-service :linux [_]
  (println "📡 Starting watcher as Linux systemd service...")
  (let [home (System/getProperty "user.home")
        systemd-dir (str home "/.config/systemd/user")]
    ;; Create systemd user directory
    (process/shell "mkdir" "-p" systemd-dir)
    ;; Copy service file
    (process/shell "cp" "qq-watcher.service" systemd-dir)
    ;; Start service
    (process/shell "systemctl" "--user" "daemon-reload")
    (process/shell "systemctl" "--user" "start" "qq-watcher")))

(defmethod start-daemon-service :default [os]
  (throw (Exception. (str "Daemon management not supported on " os))))

(defmulti stop-daemon-service
  "Stop watcher daemon service (OS-specific)"
  get-os-type)

(defmethod stop-daemon-service :macos [_]
  (process/shell "launchctl" "unload" "com.qq.watcher.plist"))

(defmethod stop-daemon-service :linux [_]
  (process/shell "systemctl" "--user" "stop" "qq-watcher"))

(defmethod stop-daemon-service :default [os]
  (println "Daemon stop not implemented for" os))

(defn daemon-status []
  "Check daemon status (cross-platform)"
  (case (get-os-type)
    :macos (let [result (process/shell {:out :string :continue true} 
                                      "launchctl" "list")]
             (if (str/includes? (:out result) "com.qq.watcher")
               :running :stopped))
    :linux (let [result (process/shell {:out :string :continue true}
                                      "systemctl" "--user" "is-active" "qq-watcher")]
             (if (= "active" (str/trim (:out result)))
               :running :stopped))
    :unknown))

(defn ensure-daemon-running []
  "Ensure watcher daemon is running, start if needed"
  (if (= (daemon-status) :running)
    (println "✅ Watcher daemon already running")
    (do
      (println "🔍 Starting watcher daemon...")
      (start-daemon-service (get-os-type))
      (Thread/sleep 3000)
      (if (= (daemon-status) :running)
        (println "✅ Watcher daemon started successfully")
        (println "❌ Failed to start watcher daemon")))))
