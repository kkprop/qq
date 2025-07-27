# QQ - Amazon Q Session Manager

A Clojure/Babashka tool for managing multiple Amazon Q conversations in separate tmux sessions with dynamic context tracking.

## Vision

Manage multiple Amazon Q sessions efficiently with:
- Independent Q windows/sessions in tmux
- Dynamic context summaries that evolve with user interaction  
- Both sync and async communication interfaces
- Session listing with context previews
- Intelligent session naming and fuzzy search

## MVP Architecture

### Core Components

1. **qq.core** - Main API namespace
2. **qq.tmux** - Tmux interaction with incremental output caching
3. **qq.session** - Session management and storage
4. **qq.naming** - Dedicated Q window for generating terse session names
5. **qq.context** - Context summarization using vacant Q windows

### Key Design Decisions

- **Session Limit**: 21 sessions max with performance warnings
- **Auto-naming**: Context → Naming Q window → terse-name-with-hyphens (≤10 words)
- **Error Recovery**: Auto-send "continue\n" on Q errors, wait for normal output
- **Session Selection**: Fuzzy search when ambiguous, return list for user retry
- **Storage**: JSON metadata in ~/.knock/qq/

## Typical User Flow

```clojure
;; Create a new session with context
(qq/create-session {:context "Analyzing AWS Lambda cold start performance issues"})
;; → {:session-id "uuid-123" :name "lambda-cold-start-analysis"}

;; Ask questions (uses current/recent session)
(qq/ask "What are the main causes of Lambda cold starts?")
;; → Returns answer synchronously

;; Create parallel session
(qq/create-session {:context "Setting up CI/CD pipeline for microservices"})
;; → {:session-id "uuid-456" :name "microservices-cicd-setup"}

;; Ambiguous query triggers session selection
(qq/ask "How do I optimize performance?")
;; → "Multiple sessions match. Please specify:
;;    1. lambda-cold-start-analysis  
;;    2. microservices-cicd-setup"

;; Specify session by fuzzy name
(qq/ask "lambda" "How do I optimize performance?")
;; → Sends to lambda session

;; List all sessions with context
(qq/list-sessions)
;; → Shows names, contexts, last activity, message counts

;; Attach to tmux session for full view
(qq/attach-session "lambda")
;; → Prints: "tmux attach -t qq-lambda-cold-start-analysis"
```

## API Design

### Core Functions

```clojure
;; Session Management
(qq/create-session {:context "Initial context description"})
(qq/list-sessions)
(qq/attach-session session-name-or-id)
(qq/switch-to session-name)  ; Change current session

;; Communication
(qq/ask "question")                    ; Current session, sync
(qq/ask "session-name" "question")     ; Specific session, sync  
(qq/ask-async "question")              ; Current session, async
(qq/ask-async "session-name" "question") ; Specific session, async

;; Context & Workflow
(qq/summarize-session session-name)   ; Trigger context summary
(qq/save-workflow "workflow-name")    ; Save current session as template
(qq/load-workflow "workflow-name")    ; Create session from template
```

### Session Selection UX

- **Current Session Concept**: Most recent or explicitly switched session
- **Fuzzy Matching**: Partial names resolve to unique sessions
- **Disambiguation**: Multiple matches return selection list
- **Error Recovery**: Auto-continue on Q errors, transparent to user

## Technical Implementation

### Session Storage Structure
```
~/.knock/qq/
├── sessions.json          # Session registry
├── sessions/
│   ├── uuid-123/
│   │   ├── metadata.json  # Name, context, timestamps
│   │   ├── context.json   # Dynamic context summary
│   │   └── tmux.log       # Cached tmux output
│   └── uuid-456/
│       └── ...
└── workflows/
    ├── aws-debugging.json
    └── cicd-setup.json
```

### Tmux Integration
- **Session Naming**: `qq-{terse-session-name}`
- **Incremental Capture**: Cache output, return only new content since last interaction
- **Error Detection**: Monitor for red ANSI codes, specific error patterns
- **Auto-recovery**: Send "continue\n" on errors, wait for normal output

### Context Evolution
- **Trigger Points**: Session inactivity, explicit summarization requests
- **Summarization**: Use dedicated naming Q window for context analysis
- **Granularity**: Session-level summaries with conversation themes
- **Persistence**: Save evolving context to session metadata

## MVP Implementation Plan

### Phase 1: Basic Session Management
- [ ] Session creation with tmux integration
- [ ] Simple ask/answer without context tracking
- [ ] Session listing and basic metadata
- [ ] Attach to tmux sessions

### Phase 2: Smart Features  
- [ ] Auto-naming via dedicated Q window
- [ ] Context summarization
- [ ] Fuzzy session selection
- [ ] Error recovery automation

### Phase 3: Advanced Features
- [ ] Workflow templates
- [ ] Context export/import
- [ ] Performance monitoring (21 session limit)
- [ ] Enhanced context visualization

## User Story

**"As a developer, I want to manage multiple Amazon Q conversations for different projects, so I can context-switch efficiently without losing conversation history."**

**Acceptance Criteria:**
- Create named Q sessions with initial context
- Ask questions to specific sessions  
- List all sessions with basic info
- Attach to tmux session to see full conversation
- Handle basic Q errors gracefully

## Development Setup

```bash
# Clone and setup
git clone https://github.com/kkprop/qq.git
cd qq

# Install dependencies (Babashka required)
bb deps

# Run tests
bb test

# Start development REPL
bb repl
```

## Contributing

This is an experimental project exploring multi-session AI conversation management. Contributions welcome!

## License

MIT License
