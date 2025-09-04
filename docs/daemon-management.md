# QQ Watcher Daemon Management

## Problem Solved

**Issue**: `bb qq q` needs to auto-start a persistent watcher daemon that survives process termination.

**Previous Failed Approaches**:
- `nohup bb watcher &` - Dies when parent exits
- `ProcessBuilder` with detached process - Not truly persistent
- Shell background processes `bb watcher > /dev/null 2>&1 &` - Dies with parent

## macOS Solution (IMPLEMENTED)

### Using launchd (macOS Service Manager)

**Files Created**:
- `com.qq.watcher.plist` - Service definition
- Auto-start code in `src/qq/interactive.clj`

**Service Definition** (`com.qq.watcher.plist`):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.qq.watcher</string>
    <key>ProgramArguments</key>
    <array>
        <string>/opt/homebrew/bin/bb</string>
        <string>watcher</string>
    </array>
    <key>WorkingDirectory</key>
    <string>/Users/dc/kkprop/qq</string>
    <key>RunAtLoad</key>
    <false/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/tmp/qq-watcher.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/qq-watcher.error.log</string>
</dict>
</plist>
```

**Auto-start Logic**:
```clojure
;; In src/qq/interactive.clj
(try
  (watcher-client/add-session-to-watcher decorated-name)
  (println "✅ Added to existing watcher daemon")
  (catch Exception _
    ;; Watcher not running, start it as launchd service
    (println "📡 Starting watcher as launchd service...")
    (process/shell {:dir (System/getProperty "user.dir")} 
                  "launchctl" "load" "com.qq.watcher.plist")
    (Thread/sleep 3000)
    (watcher-client/add-session-to-watcher decorated-name)
    (println "✅ Started new watcher daemon")))
```

**Benefits**:
- ✅ Survives process termination
- ✅ Auto-restarts if crashes (`KeepAlive=true`)
- ✅ Proper logging to `/tmp/qq-watcher.log`
- ✅ Native macOS service integration

## Linux Solution (TODO)

### Using systemd (Linux Service Manager)

**Files Needed**:
- `qq-watcher.service` - systemd service unit
- Cross-platform detection in `src/qq/interactive.clj`

**Service Definition** (`qq-watcher.service`):
```ini
[Unit]
Description=QQ Watcher Daemon
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/bb watcher
WorkingDirectory=/path/to/qq
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**Auto-start Logic** (Linux):
```clojure
;; Cross-platform daemon start
(defn start-watcher-daemon []
  (cond
    (= (System/getProperty "os.name") "Mac OS X")
    (process/shell "launchctl" "load" "com.qq.watcher.plist")
    
    (str/includes? (System/getProperty "os.name") "Linux")
    (do
      ;; Copy service file to user systemd directory
      (process/shell "mkdir" "-p" (str (System/getProperty "user.home") "/.config/systemd/user"))
      (process/shell "cp" "qq-watcher.service" 
                    (str (System/getProperty "user.home") "/.config/systemd/user/"))
      ;; Start service
      (process/shell "systemctl" "--user" "daemon-reload")
      (process/shell "systemctl" "--user" "start" "qq-watcher"))
    
    :else
    (throw (Exception. "Unsupported OS for daemon management"))))
```

## Cross-Platform Implementation Plan

### 1. OS Detection
```clojure
(defn get-os-type []
  (let [os-name (System/getProperty "os.name")]
    (cond
      (= os-name "Mac OS X") :macos
      (str/includes? os-name "Linux") :linux
      (str/includes? os-name "Windows") :windows
      :else :unknown)))
```

### 2. Service File Templates
- **macOS**: `templates/com.qq.watcher.plist.template`
- **Linux**: `templates/qq-watcher.service.template`
- Replace `{{BB_PATH}}` and `{{WORKING_DIR}}` at runtime

### 3. Cross-Platform Service Management
```clojure
(defmulti start-daemon-service get-os-type)

(defmethod start-daemon-service :macos [_]
  (process/shell "launchctl" "load" "com.qq.watcher.plist"))

(defmethod start-daemon-service :linux [_]
  (process/shell "systemctl" "--user" "start" "qq-watcher"))

(defmethod start-daemon-service :default [os]
  (throw (Exception. (str "Daemon management not supported on " os))))
```

## Windows Solution (FUTURE)

### Using Windows Service Manager
- **sc.exe** for service creation
- **NSSM** (Non-Sucking Service Manager) as wrapper
- **PowerShell** service management

## Implementation Status

- ✅ **macOS**: Complete with launchd
- ⏳ **Linux**: Design ready, needs implementation
- ⏳ **Windows**: Design needed
- ⏳ **Cross-platform**: OS detection needed

## Testing Commands

### macOS
```bash
# Manual service management
launchctl load com.qq.watcher.plist
launchctl unload com.qq.watcher.plist
launchctl list | grep qq.watcher

# Check logs
cat /tmp/qq-watcher.log
```

### Linux (Future)
```bash
# Manual service management
systemctl --user start qq-watcher
systemctl --user stop qq-watcher
systemctl --user status qq-watcher

# Check logs
journalctl --user -u qq-watcher -f
```

## Benefits of This Approach

1. **True Persistence** - Services survive parent process termination
2. **Auto-Recovery** - Services restart on crashes
3. **Proper Logging** - System-integrated log management
4. **OS Native** - Uses each OS's standard service management
5. **User Experience** - Single `bb qq q` command handles everything

This solution transforms QQ from a simple script to a professional daemon-managed system.
