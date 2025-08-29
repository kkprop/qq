# QQ - Multi-Window Tmux Web Terminal

🎯 **A complete web-native tmux experience with intelligent command routing and multi-user optimization.**

*Evolved from Amazon Q Session Manager to a full-featured multi-window tmux web interface.*

## ✨ Latest Features (Major Update!)

### 🎮 **Enhanced Interactive TUI (NEW!)**
- **Gum-style API** - Zero configuration required, just pass data
- **Three-level compatibility** - Simple to advanced customization
- **Universal data support** - Strings, keywords, maps, functions
- **Real-time filtering** with instant character input
- **Multiple navigation options**: ↑↓ arrows, Ctrl+P/N (Emacs), Ctrl+K (Vim)
- **Blinking cursor feedback** with professional visual polish
- **Zero key collisions** - type any character including 'q' to filter
- **Fuzzy matching** with instant session filtering

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

## 🎮 **Interactive TUI System**

QQ features a professional-grade Terminal User Interface built from scratch with gum-style interactions and zero flicker rendering.

### **Core TUI Features**

#### **🔍 Real-Time Filtering**
- **Instant character input** - Type any character for immediate filtering
- **Fuzzy matching** - Find sessions with partial character sequences
- **Visual feedback** - Live filter display with blinking cursor
- **Zero key collisions** - All printable characters available for filtering

#### **⌨️ Multiple Navigation Styles**
- **Arrow Keys** - ↑↓ for universal navigation
- **Emacs Style** - Ctrl+P (previous) / Ctrl+N (next)
- **Vim Style** - Ctrl+K (up) for vim users
- **Consistent behavior** across all navigation methods

#### **🎨 Visual Polish**
- **Blinking cursor** - Professional 500ms blink cycle
- **Session status** - 🟢 active / ⚪ inactive indicators  
- **Pane count display** - Shows window/pane information
- **Clean help text** - Context-sensitive key bindings

### **TUI Architecture**

#### **🏗️ Framework Design**
- **Reactive state management** - Atom-based single source of truth
- **Differential rendering** - Only updates when content changes
- **Flicker-free display** - Professional-grade visual experience
- **Terminal compatibility** - Raw mode with proper cleanup

#### **🔧 Technical Implementation**
```clojure
; Core TUI loop
State Atom → Watch → Content Compare → Render (if changed) → Terminal
     ↑                                                           ↓
Event Loop ← Input ← Terminal ←──────────────────────────────────┘
```

#### **⚡ Performance Characteristics**
- **Zero flicker** - Differential rendering eliminates screen flash
- **Low CPU usage** - Only renders on actual state changes
- **Responsive input** - 50ms polling with non-blocking reads
- **Smooth transitions** - Content changes appear instantly

### **TUI Key Bindings**

#### **🔍 Filtering Controls**
- **Any character** → Filter sessions instantly
- **Backspace** → Delete filter characters
- **Escape** → Clear entire filter

#### **📍 Navigation Controls**
- **↑↓ Arrows** → Navigate filtered results
- **Ctrl+P** → Previous (Emacs style)
- **Ctrl+N** → Next (Emacs style)  
- **Ctrl+K** → Up (Vim style)

#### **🎯 Action Controls**
- **Enter** → Select and attach to session
- **Ctrl+C** → Quit TUI (no key collisions)

### **TUI Usage Examples**

#### **Basic Session Selection**
```bash
bb qq                    # Start interactive selector
# Type characters to filter
# Use arrows/Ctrl+P/N to navigate
# Press Enter to attach
```

#### **Quick Filtering**
```bash
bb qq
# Type "demo" → Shows only sessions with "demo"
# Type "q" → Shows sessions with "q" (no quit collision)
# Escape → Clear filter, show all sessions
```

#### **Multi-Style Navigation**
```bash
bb qq
# Arrow users: ↑↓ to navigate
# Emacs users: Ctrl+P/Ctrl+N to navigate  
# Vim users: Ctrl+K to go up
# All styles work identically
```

### **TUI Development**

#### **🧪 Testing Methodology**
- **tmux-based testing** - Real environment validation
- **Visual confirmation** - Direct output inspection
- **Incremental development** - One feature at a time
- **Debug-driven approach** - Minimal debug scripts when needed

#### **🔧 Technical Challenges Solved**
- **Character input detection** - `case` vs `cond` for predicates
- **Key collision resolution** - Removed special 'q' handling
- **Cursor blinking** - Reactive state with background threads
- **Terminal raw mode** - Proper stty setup and cleanup

## 🚀 Quick Start

### Enhanced Interactive TUI (NEW!)
```bash
# Interactive session selector with gum-style filtering
bb qq

# Features:
# - Type any character to filter sessions instantly
# - ↑↓/Ctrl+P/Ctrl+N/Ctrl+K for navigation  
# - Backspace to delete, Escape to clear filter
# - Enter to select session, Ctrl+C to quit
```

### Web Terminal Interface
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

### 🎮 **Gum-Style Interactive TUI Implementation**
- Built professional-grade filtering with real-time character input
- Implemented multiple navigation key bindings (arrows, Emacs, Vim styles)
- Added blinking cursor and visual feedback systems
- Solved key collision issues for seamless user experience

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

- **[TUI Enhancement Log](docs/tui-enhancement-log.md)** - Complete implementation journey and test methodology
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
