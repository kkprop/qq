# QQ - Multi-Window Tmux Web Terminal

🎯 **A complete web-native tmux experience with intelligent command routing and multi-user optimization.**

*Evolved from Amazon Q Session Manager to a full-featured multi-window tmux web interface.*

## ✨ Latest Features (Major Update!)

### 🏷️ **Web-Native Tab Interface**
- **Clickable tabs** instead of dropdown menus
- **Visual activity indicators** with colored dots
- **Instant window switching** with perfect content sync
- **Browser-like UX** optimized for web users

### 📺 **Server-Side Window Subscription System**
- **Bandwidth optimized** for multi-user scenarios (<10 users)
- **Window-specific streaming** - only relevant updates sent
- **Targeted broadcasting** - users receive only their window's data
- **Dynamic subscription management** based on user selections

### 🎯 **Smart Command Routing**
- **Q commands** (`what`, `how`, `explain`, `?`) → Main session with Q&A
- **Shell commands** (`ls`, `cd`, `echo`) → Currently viewed window
- **Intelligent detection** based on command patterns
- **Preserves Q&A functionality** while enabling multi-window workflow

### 🔧 **Robust Error Handling**
- **Graceful browser cleanup** - no JSON parsing errors
- **Connection resilience** - handles window close/refresh smoothly
- **Dead connection cleanup** - automatic resource management
- **Error-resistant WebSocket communication**

## 🚀 Quick Start

### Web Terminal Interface (NEW!)
```bash
# Start tmux sessions
bb setup-tmux-sessions

# Start WebSocket server
bb start-terminal-server

# Open web interface
open http://localhost:9090/web/terminal.html
```

### Traditional CLI Interface
```bash
# Create Q session
bb create "Working on AWS Lambda optimization"

# Ask questions
bb ask "What causes Lambda cold starts?"

# List sessions
bb list
```

## 🏗️ Architecture

### Multi-Window Web System
```
Browser Tabs ←→ WebSocket ←→ Server ←→ Tmux Windows
     ↓              ↓           ↓         ↓
  Tab UI    Window Subscription  Smart    Individual
  Activity   Management         Routing   Windows
  Tracking                      System
```

### Smart Routing Flow
```
User Command → Command Detection → Route Decision
     ↓              ↓                    ↓
Q Command?     Pattern Match      Main Session (Q&A)
Shell Command? Content Analysis   Current Window
```

## 🎯 Usage

### Web Interface
- **Click tabs** to switch between tmux windows instantly
- **Orange dots** indicate windows with new activity
- **Green highlight** shows currently active window
- **+ New button** creates additional tmux windows

### Command Types
- **Q Commands**: `what is Clojure?`, `explain functions`, `how to...`
- **Shell Commands**: `ls -la`, `cd /path`, `tail -f log.txt`
- **Automatic routing** based on command content

### Multi-User Support
- Multiple users can connect simultaneously
- Each user sees only their selected window's updates
- Bandwidth optimized for <10 concurrent users
- Independent window subscriptions per user

## 📈 Recent Major Achievements

### 🎨 **Tab-Style Interface Implementation**
- Replaced dropdown with clickable tabs
- Added visual activity indicators
- Implemented hover effects and smooth transitions
- Created web-native user experience

### 📺 **Server-Side Window Subscription System**
- Built user window subscription tracking
- Implemented window-specific tmux streaming
- Created targeted broadcasting for bandwidth optimization
- Added dynamic streaming management

### 🎯 **Smart Command Routing System**
- Developed intelligent command detection
- Implemented Q vs shell command routing
- Preserved Q&A functionality with multi-window support
- Created session-aware command processing

### 🔧 **Robust Error Handling**
- Fixed JSON parsing errors during browser cleanup
- Implemented graceful connection termination
- Added comprehensive error logging
- Created resilient WebSocket communication

## 🎯 Performance

- **Fluent operation** with instant window switching
- **Bandwidth optimized** for multi-user scenarios
- **Error-resistant** connection handling
- **Perfect content synchronization** with tmux

## Core Features (Original)

- **🚀 Multiple Q Sessions**: Independent Q conversations in separate tmux sessions
- **🏷️ Smart Naming**: Auto-generated terse session names using Q itself
- **🔍 Fuzzy Search**: Find sessions by partial name matching
- **📊 Context Tracking**: Dynamic conversation summaries and theme extraction
- **🔄 Error Recovery**: Auto-continue on Q errors for smooth operation
- **💾 Persistent Storage**: Sessions saved in `~/.knock/qq/` with full history

## Available Commands

```bash
bb create "context description"  # Create new Q session
bb ask "question"               # Ask current session
bb list                         # List all sessions with summaries
bb switch session-name          # Switch active session
bb attach session-name          # Get tmux attach command
bb dev-repl                     # Start development REPL
```

## Architecture

QQ consists of 5 core components:
- **qq.core** - Main API and CLI interface
- **qq.session** - Session management and persistence
- **qq.tmux** - Tmux integration and output capture
- **qq.naming** - Intelligent session naming service
- **qq.context** - Dynamic context tracking and summarization

## Documentation

- **[Default Window Design](docs/default-window-design.md)** - Upcoming feature for immediate Q access
- **[Architecture](docs/architecture.md)** - Detailed system design and component overview
- **[Development Log](docs/development-log.md)** - Progress tracking and technical decisions

## Current Status

**MVP Status**: ✅ Core functionality validated and working

**Working Features**:
- Session creation and management
- Tmux integration with proper session handling
- CLI interface with clean output
- JSON persistence and session listing

**In Progress**:
- Default window for immediate access
- Context summarization improvements
- Enhanced naming service reliability

## Development

```bash
# Run tests
bb test

# Start development REPL
bb dev-repl

# Check available tasks
bb tasks
```

## Requirements

- [Babashka](https://babashka.org/) - Clojure scripting environment
- [tmux](https://github.com/tmux/tmux) - Terminal multiplexer
- [Amazon Q CLI](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/command-line-getting-started.html) - `q chat` command

## Contributing

This is an experimental project exploring multi-session AI conversation management. See [Development Log](docs/development-log.md) for current priorities and technical decisions.

## License

MIT License
