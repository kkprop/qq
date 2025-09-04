# QQ Timeline Logging Design

*Real-time Q&A interaction capture and JSONL timeline generation*

## 🎯 Overview

The QQ Timeline Logging system captures all user questions from Q chat sessions in real-time and logs them to structured JSONL files. This enables conversation history tracking, analysis, and replay capabilities.

## 🏗️ Architecture

### **Core Components**

```
User Input → tmux pipe-pane → Stream File → Watcher Daemon → Timeline JSONL
     ↓              ↓             ↓              ↓              ↓
  Q Session    Raw Capture   File Monitor   Extraction    Structured Log
```

### **File Structure**
```
log/
├── {session-name}/
│   └── timeline.jsonl          # Structured Q&A interactions
└── tmp/
    └── qq-stream-{session}.log # Raw tmux capture (temporary)
```

## 📊 Data Format

### **Timeline JSONL Entry**
```json
{
  "timestamp": "2025-09-04T03:12:12.866887Z",
  "type": "question",
  "content": "what is the current time?",
  "user": "human"
}
```

### **Field Specifications**
- **`timestamp`**: ISO 8601 UTC timestamp when question was captured
- **`type`**: Always `"question"` (future: may include `"answer"`, `"system"`)
- **`content`**: Clean question text with ANSI codes removed
- **`user`**: Always `"human"` (distinguishes from potential Q responses)

## 🔧 Technical Implementation

### **1. tmux Integration**
```bash
tmux pipe-pane -t {session} -o "cat >> {absolute-path}/log/tmp/qq-stream-{session}.log"
```
- **Captures all terminal output** including user input and Q responses
- **Requires absolute paths** - relative paths fail
- **Continuous streaming** - appends to log file in real-time

### **2. Question Extraction**
```clojure
(defn extract-human-questions [output]
  "Extract human questions using simple line-based approach"
  (->> (str/split-lines output)
       (filter #(str/ends-with? % "\u001B[?2004l"))  ; User input marker
       (map strip-ansi-and-clean)                    ; Remove ANSI codes
       (filter valid-question?)                      ; Filter noise
       (distinct)))                                  ; Remove duplicates
```

**Key Insight**: Lines ending with `\u001B[?2004l` are user input in Q sessions.

### **3. Real-time File Monitoring**
```clojure
(defn start-direct-watcher [session-name]
  (let [stream-file (absolute-path)
        last-size (atom 0)]
    (while true
      (when (file-grew? stream-file last-size)
        (process-new-content stream-file last-size))
      (Thread/sleep 500))))  ; 500ms polling
```

**Polling Strategy**: Simple file size monitoring with 500ms intervals.

### **4. Automatic Integration**
```clojure
(defn create-or-attach-session [session-name]
  ; Create/attach to tmux session
  (tmux-new-session session-name)
  ; Auto-add to watcher daemon
  (auto-add-to-watcher session-name))
```

**User Experience**: `bb qq session-name` automatically enables logging.

## 🚀 Usage Workflow

### **Setup (One-time)**
```bash
bb watcher                    # Start watcher daemon
```

### **Session Creation**
```bash
bb qq my-session             # Creates session + auto-enables logging
```

### **Automatic Logging**
- User types questions in Q session
- Questions immediately appear in `log/my-session/timeline.jsonl`
- No manual intervention required

## 📈 Benefits

### **For Users**
- **Zero configuration** - Works automatically after `bb watcher`
- **Complete history** - All questions captured with timestamps
- **Searchable logs** - JSONL format enables easy analysis
- **Session isolation** - Each session has separate timeline

### **For Developers**
- **Simple architecture** - Line-based extraction, no complex regex
- **Reliable capture** - tmux pipe-pane ensures nothing is missed
- **Extensible format** - JSONL allows easy addition of new fields
- **Debug friendly** - Enhanced logging shows full processing flow

## 🔍 Design Decisions

### **Why JSONL over JSON?**
- **Streaming friendly** - Can append entries without parsing entire file
- **Line-based processing** - Easy to tail, grep, and analyze
- **Fault tolerant** - Corrupted entry doesn't break entire file

### **Why Simple Line Detection?**
- **Reliable** - `\u001B[?2004l` consistently marks user input
- **Fast** - No complex regex parsing required
- **Maintainable** - Easy to understand and debug

### **Why Absolute Paths?**
- **tmux requirement** - pipe-pane fails with relative paths
- **Reliability** - Eliminates working directory issues
- **Consistency** - Same behavior regardless of execution context

### **Why 500ms Polling?**
- **Responsive** - Near real-time capture (< 1 second delay)
- **Efficient** - Low CPU overhead compared to file watching
- **Simple** - No complex file system event handling

## 🛠️ Troubleshooting

### **No Timeline Files Created**
1. Check watcher daemon: `ps aux | grep "bb watcher"`
2. Verify session added: `bb watcher-add session-name`
3. Check pipe-pane: `tmux show-options -t session | grep pipe`

### **Empty Timeline Files**
1. Verify stream file exists: `ls -la log/tmp/qq-stream-*.log`
2. Check stream file growth: Send test command and verify file size increases
3. Review watcher logs for extraction errors

### **Missing Questions**
1. Confirm question format: Must end with `\u001B[?2004l`
2. Check ANSI filtering: Complex formatting may be filtered out
3. Verify polling frequency: 500ms delay means slight capture lag

## 🔮 Future Enhancements

### **Planned Features**
- **Answer capture** - Log Q responses alongside questions
- **Response timing** - Track question → answer latency
- **Session metadata** - Context, creation time, participant info
- **Export formats** - Convert JSONL to CSV, Markdown, etc.

### **Potential Improvements**
- **File watching** - Replace polling with inotify/fsevents
- **Compression** - Rotate and compress old timeline files
- **Analytics** - Built-in question analysis and reporting
- **Multi-user** - Support for shared session logging

## 📚 Related Documentation

- **[Architecture](architecture.md)** - Overall system design
- **[Development Timeline](timeline.md)** - Implementation history
- **[TUI Design](tui-design.md)** - Interactive session management
- **[Daemon Management](daemon-management.md)** - Watcher daemon operations

---

**The QQ Timeline Logging system provides comprehensive, real-time Q&A interaction capture with minimal user overhead and maximum reliability.**
