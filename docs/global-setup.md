# Global QQ Setup - Universal Directory Access

## 🎯 **Problem Solved**

### **Directory Context Mismatch**
- **Users work in project directories** - `/my/project`, `/work/code`, etc.
- **QQ commands required qq project directory** - Must `cd /path/to/qq`
- **Q sessions started in wrong context** - Inherited qq project directory instead of user's working directory

### **Solution: Global `bq` Alias**
Use Babashka's `--config` flag to access qq commands from any directory while preserving working directory context.

## 🚀 **Implementation**

### **Automated Setup Command**
```bash
bb setup  # One-time setup adds global 'bq' alias
```

### **Smart System Detection**
The setup process intelligently detects user environment:

#### **Shell Detection Logic**
```clojure
(defn detect-shell []
  (let [shell (or (System/getenv "SHELL") "/bin/bash")]
    (cond
      (str/includes? shell "zsh") "~/.zshrc"
      (str/includes? shell "bash") "~/.bashrc"
      :else "~/.profile")))
```

#### **Path Resolution**
```clojure
(defn get-qq-path []
  (System/getProperty "user.dir"))  ; Current qq project location

(defn create-alias-line []
  (str "alias bq='bb --config " (get-qq-path) "/bb.edn'"))
```

#### **Duplicate Prevention**
```clojure
(defn alias-exists? [profile-file]
  (when (.exists (io/file profile-file))
    (str/includes? (slurp profile-file) "alias bq=")))
```

### **Step-by-Step System Information Collection**

#### **Setup Process Flow**
1. **🔧 Detect Shell** - Identify user's shell environment
2. **📁 Resolve Paths** - Get qq project absolute path
3. **🔗 Generate Alias** - Create proper alias command
4. **✅ Check Existing** - Prevent duplicate entries
5. **📝 Write Profile** - Add alias to shell configuration
6. **🔄 Provide Instructions** - Guide user on activation

#### **User-Friendly Output**
```
🔧 Setting up global 'bq' alias...
📁 QQ Path: /path/to/qq
📝 Profile: ~/.zshrc
🔗 Alias: alias bq='bb --config /path/to/qq/bb.edn'
✅ Alias added to ~/.zshrc
🔄 Run 'source ~/.zshrc' or restart your terminal
🚀 Then use 'bq qq' from any directory!
```

## 🎯 **Usage After Setup**

### **Global Access Pattern**
```bash
# From any directory
cd /my/project
bq qq my-work        # Creates qq-my-work in /my/project
bq qq                # Interactive selector
bq q                 # Direct Q chat access
```

### **Working Directory Preservation**
```bash
cd /work/important-project
bq qq urgent-fix     # Q session starts in /work/important-project
```

## 🔧 **Technical Benefits**

### **Babashka --config Approach**
- **No code duplication** - Uses existing bb.edn tasks
- **Path independence** - Works regardless of current directory
- **Context preservation** - Q sessions inherit user's working directory
- **Zero performance overhead** - Direct babashka execution

### **Smart Shell Integration**
- **Cross-platform compatibility** - Works with zsh, bash, and generic shells
- **Safe installation** - Prevents duplicate aliases
- **User guidance** - Clear activation instructions
- **Manual fallback** - Provides manual setup commands if needed

### **System Information Collection Strategy**
The setup process demonstrates excellent system integration by:

1. **Environment Detection** - `$SHELL` variable analysis
2. **Path Resolution** - Dynamic project path detection  
3. **File System Safety** - Existence checks before modification
4. **User Communication** - Step-by-step progress reporting
5. **Error Handling** - Graceful fallbacks and manual instructions

## 📋 **Complete Workflow Achievement**

| User Need | Before | After |
|-----------|--------|-------|
| **Work in project** | ❌ Must cd to qq dir | ✅ Works from anywhere |
| **Q session context** | ❌ Wrong directory | ✅ Correct working dir |
| **Setup complexity** | ❌ Manual alias creation | ✅ Automated `bb setup` |
| **Cross-platform** | ❌ Shell-specific | ✅ Universal detection |

## 🎉 **Result**

**Perfect directory context management** - Users can access qq functionality from any directory while maintaining proper working directory context for their Q sessions.

**One-time setup, lifetime convenience** - Single `bb setup` command provides permanent global access with intelligent system integration.
