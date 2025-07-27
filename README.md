# QQ - Amazon Q Session Manager

A Clojure/Babashka tool for managing multiple Amazon Q conversations in separate tmux sessions with dynamic context tracking.

## Quick Start

```bash
# Install dependencies
bb deps

# Create your first Q session
bb create "Working on AWS Lambda optimization"

# Ask questions
bb ask "What causes Lambda cold starts?"

# List all sessions
bb list

# Switch between sessions
bb switch lambda-optimization

# Attach to tmux session to see full conversation
bb attach lambda-optimization
```

## Core Features

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
