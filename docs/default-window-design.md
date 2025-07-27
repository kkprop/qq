# Default Window Design

## Overview

Provide users with a default Q window that's always available, eliminating the need to create a session before asking questions. This improves the user experience by allowing immediate interaction.

## User Experience Goals

- **Immediate Access**: Users can ask questions without setup
- **Seamless Workflow**: Default window works alongside named sessions
- **Persistent Context**: Default session persists across restarts
- **Clear Mental Model**: Users understand default vs named sessions

## Proposed User Flow

### **Immediate Usage (New)**
```bash
# User can immediately ask without creating session
bb ask "What are Lambda cold starts?"
# → Uses default window, creates it if needed

# Default window persists for follow-up questions
bb ask "How do I optimize them?"
# → Continues in same default session
```

### **Named Sessions (Existing)**
```bash
# User can still create named sessions for specific contexts
bb create "AWS cost optimization project"
# → Creates dedicated session alongside default

# Switch between sessions
bb switch cost-optimization
bb ask "How do I reduce Lambda costs?"
# → Uses named session

bb switch-to-default  # New command
bb ask "General question"
# → Back to default window
```

## Implementation Design

### **Session Priority Logic**
```clojure
(defn get-active-session []
  (or @current-session          ; Explicitly switched session
      (find-default-session)   ; Default window if exists
      (create-default-session))) ; Create default if none exists
```

### **Default Session Properties**
- **Session ID**: `"default"`
- **Tmux Name**: `qq-default`
- **Context**: `"General Amazon Q assistance"`
- **Persistence**: Saved like other sessions but marked as default
- **Auto-creation**: Created on first `bb ask` if no sessions exist

### **Session Management**
```clojure
;; Core functions (updated)
(qq/ask "question")              ; Uses current session or creates default
(qq/create-session {:context})   ; Creates named session, switches to it
(qq/switch-to session-name)      ; Switch to named session
(qq/list-sessions)               ; Shows all sessions, marks default

;; New default-specific functions
(qq/ask-default "question")      ; Always uses default window
(qq/switch-to-default)           ; Switch current to default
(qq/reset-default)               ; Clear default session history
(qq/ensure-default)              ; Create default if not exists
```

## Technical Implementation

### **Default Session Creation**
```clojure
(defn create-default-session []
  "Create the default Q session if it doesn't exist"
  (let [default-session {:id "default"
                        :name "default"
                        :context "General Amazon Q assistance"
                        :created-at (System/currentTimeMillis)
                        :last-activity (System/currentTimeMillis)
                        :message-count 0
                        :is-default true}]
    (tmux/create-session "default")
    (session/save default-session)
    "default"))
```

### **Session Resolution Logic**
```clojure
(defn resolve-active-session []
  "Get the session to use for commands"
  (cond
    ;; User explicitly switched to a session
    @current-session @current-session
    
    ;; Default session exists
    (session/exists? "default") "default"
    
    ;; No sessions exist - create default
    :else (create-default-session)))
```

### **Updated CLI Commands**
```bash
# Existing commands (behavior updated)
bb ask "question"           # Uses active session (default or switched)
bb list                     # Shows all sessions, marks default with *
bb switch session-name      # Switch to named session

# New commands
bb ask-default "question"   # Force use default window
bb switch-default           # Switch current to default
bb reset-default            # Clear default session
```

## Display Changes

### **Session Listing with Default**
```
📋 Amazon Q Sessions:
=====================
* → default (5 msgs, 2m ago)
    General Amazon Q assistance
    
    aws-cost-optimization (12 msgs, 1h ago)
    Analyzing AWS Lambda cold start performance issues
    
    cicd-pipeline (3 msgs, 1d ago)
    Setting up CI/CD pipeline for microservices
```

**Legend:**
- `*` = Default session
- `→` = Current active session

### **Auto-creation Messages**
```bash
$ bb ask "What is Lambda?"
🚀 Creating default Q session...
✅ Default session ready
🤖 What is Lambda?
[Q's response...]
```

## Benefits

1. **Lower Barrier to Entry**: No setup required for first use
2. **Familiar Pattern**: Similar to terminal/REPL default behavior  
3. **Flexible Workflow**: Works with both casual and structured usage
4. **Persistent Context**: Default session maintains conversation history
5. **Clear Organization**: Named sessions for projects, default for general use

## Migration Strategy

### **Existing Users**
- Current sessions continue to work unchanged
- Default session created on first `bb ask` after update
- No breaking changes to existing commands

### **New Users**
- Can immediately start with `bb ask "question"`
- Natural progression to named sessions when needed
- Intuitive mental model from first use

## Open Questions

1. **Default Context**: Should it be configurable by user?
2. **Auto-cleanup**: Should default session auto-clear after inactivity?
3. **Session Limits**: Does default count toward 21 session limit?
4. **Naming**: Better name than "default"? ("general", "main", "quick"?)
5. **Persistence**: Same persistence rules as named sessions?

## Next Steps

1. Implement `create-default-session` function
2. Update `ask` command to use session resolution logic
3. Add new CLI commands for default session management
4. Update session listing to show default marker
5. Test user workflow with tmux integration
6. Update documentation and help text
