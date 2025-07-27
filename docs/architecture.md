# QQ Architecture Documentation

## System Overview

QQ is a session manager for Amazon Q conversations, built with Clojure/Babashka. It manages multiple Q instances in separate tmux sessions with intelligent context tracking and naming.

## Core Components

### **qq.core** - Main API Layer
- **Purpose**: Primary user interface and command orchestration
- **Responsibilities**:
  - CLI command handling (`-main` function)
  - Session lifecycle management (create, switch, list)
  - User interaction coordination
  - Current session state management
- **Key Functions**:
  - `create-session` - Create new Q session with context
  - `ask` / `ask-async` - Send questions to sessions
  - `list-sessions` - Display all sessions with summaries
  - `switch-to` - Change active session

### **qq.session** - Session Management
- **Purpose**: Session persistence and metadata management
- **Responsibilities**:
  - Session storage (JSON files in `~/.knock/qq/`)
  - Session registry maintenance
  - Fuzzy name resolution
  - Activity tracking and timestamps
- **Storage Structure**:
  ```
  ~/.knock/qq/
  ├── sessions.json          # Session registry
  ├── sessions/
  │   ├── {uuid}/
  │   │   ├── metadata.json  # Session details
  │   │   ├── context.json   # Dynamic context
  │   │   └── tmux.log       # Cached output
  ```

### **qq.tmux** - Tmux Integration
- **Purpose**: Tmux session management and output capture
- **Responsibilities**:
  - Create/destroy tmux sessions
  - Send commands to Q instances
  - Capture and cache output incrementally
  - Error detection and auto-recovery
- **Session Naming**: `qq-{session-name}` or `qq-{uuid}`
- **Error Recovery**: Auto-send "continue\n" on Q errors

### **qq.naming** - Intelligent Naming Service
- **Purpose**: Generate terse, descriptive session names
- **Implementation**: Dedicated Q window (`qq-naming-service`)
- **Process**:
  1. Send context to naming Q window
  2. Request terse name (≤10 words, hyphen-separated)
  3. Clean and validate generated name
  4. Fallback to timestamp-based name if generation fails

### **qq.context** - Context Tracking
- **Purpose**: Dynamic context summarization and theme tracking
- **Responsibilities**:
  - Generate conversation summaries using Q itself
  - Extract key themes and topics
  - Track context evolution over time
  - Determine when context updates are needed
- **Triggers**: Session inactivity, message count thresholds

## Data Flow

### **Session Creation Flow**
```
User: bb create "context"
  ↓
qq.core/create-session
  ↓
qq.tmux/create-session → Creates tmux session
  ↓
qq.naming/generate-name → Gets terse name
  ↓
qq.session/save → Persists metadata
  ↓
Return session info to user
```

### **Question Flow**
```
User: bb ask "question"
  ↓
qq.core/ask
  ↓
qq.session/resolve-name → Find target session
  ↓
qq.tmux/send-and-wait → Send to Q, wait for response
  ↓
qq.session/update-activity → Update timestamps
  ↓
Return response to user
```

### **Context Evolution Flow**
```
Session Activity
  ↓
qq.context/should-update-context? → Check if update needed
  ↓
qq.tmux/capture-output → Get conversation history
  ↓
qq.tmux/send-and-wait → Ask Q to summarize itself
  ↓
qq.context/extract-themes → Parse themes from summary
  ↓
qq.context/save-context → Persist updated context
```

## Configuration

### **System Limits**
- **Max Sessions**: 21 (with performance warnings)
- **Session Timeout**: 5 minutes for Q responses
- **Context Update**: Every 10 messages or 1 hour of inactivity

### **Storage Locations**
- **Base Directory**: `~/.knock/qq/`
- **Session Registry**: `sessions.json`
- **Session Data**: `sessions/{uuid}/`
- **Tmux Logs**: `sessions/{uuid}/tmux.log`

### **Tmux Conventions**
- **Session Prefix**: `qq-`
- **Naming Service**: `qq-naming-service`
- **Default Session**: `qq-default`
- **User Sessions**: `qq-{terse-name}` or `qq-{uuid}`

## Error Handling

### **Q Error Recovery**
- **Detection**: Red ANSI codes, "Error:" patterns
- **Recovery**: Auto-send "continue\n" command
- **Timeout**: 5-minute response timeout
- **Fallback**: Return timeout message to user

### **Tmux Session Management**
- **Session Conflicts**: Check existence before creation
- **Lost Sessions**: Recreate if tmux session missing
- **Cleanup**: Graceful session termination

### **Storage Resilience**
- **JSON Corruption**: Fallback to empty registry
- **Directory Creation**: Auto-create missing directories
- **Permission Issues**: Graceful degradation with warnings

## Performance Considerations

### **Session Limits**
- **21 Session Warning**: Alert users approaching limit
- **Memory Usage**: Each session maintains tmux process
- **Context Caching**: Avoid repeated summarization

### **Incremental Output**
- **Tmux Capture**: Only capture new content since last interaction
- **Response Caching**: Cache Q responses to avoid re-querying
- **Lazy Loading**: Load session metadata on demand

## Security Considerations

### **Local Storage**
- **File Permissions**: Restrict access to user only
- **Sensitive Data**: No credentials stored in session data
- **Cleanup**: Provide commands to clear session history

### **Process Isolation**
- **Tmux Sessions**: Each Q instance isolated in separate session
- **User Context**: All sessions run under user's permissions
- **Network**: Q handles its own authentication

## Extension Points

### **Plugin Architecture**
- **Custom Naming**: Pluggable naming strategies
- **Context Processors**: Custom context analysis
- **Storage Backends**: Alternative storage implementations
- **Output Formatters**: Custom response formatting

### **Integration Hooks**
- **Pre/Post Commands**: Hooks for session lifecycle events
- **Custom Workflows**: Template-based session creation
- **External Tools**: Integration with other development tools

## Monitoring and Debugging

### **Logging Strategy**
- **Debug Logs**: Detailed operation logging (optional)
- **Error Tracking**: Capture and report errors gracefully
- **Performance Metrics**: Session creation/response times

### **Diagnostic Commands**
- **Session Health**: Check tmux session status
- **Storage Integrity**: Validate session data consistency
- **Context Analysis**: Inspect context evolution

## Future Architecture Considerations

### **Scalability**
- **Remote Sessions**: Support for remote Q instances
- **Session Sharing**: Multi-user session collaboration
- **Distributed Storage**: Cloud-based session persistence

### **Advanced Features**
- **Session Templates**: Pre-configured session types
- **Smart Routing**: Auto-select best session for queries
- **Context Similarity**: Find related sessions by context
