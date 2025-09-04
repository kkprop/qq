#!/bin/bash
# QQ Watcher Status Script

echo "🔍 QQ Watcher Status"
echo "===================="

# Check if watcher is running
WATCHER_PID=$(pgrep -f "bb watcher" 2>/dev/null)
if [ -n "$WATCHER_PID" ]; then
    echo "✅ Watcher running (PID: $WATCHER_PID)"
    
    # Check nREPL port
    if netstat -an 2>/dev/null | grep -q "127.0.0.1.7888.*LISTEN"; then
        echo "✅ nREPL server active on port 7888"
    else
        echo "❌ nREPL server not responding on port 7888"
    fi
    
    # Count watched sessions
    SESSION_COUNT=$(tmux list-sessions 2>/dev/null | grep -c "qq-" || echo "0")
    echo "📊 Monitoring $SESSION_COUNT Q sessions"
    
    # Show recent log activity
    if [ -f "watcher.log" ]; then
        echo ""
        echo "📋 Recent activity (last 3 lines):"
        tail -3 watcher.log
    fi
else
    echo "❌ Watcher not running"
    echo "💡 Start with: bb watcher"
fi
