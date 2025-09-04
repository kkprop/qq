#!/bin/bash
# QQ Watcher Restart Script

echo "🔄 Restarting QQ Watcher..."

# Kill existing watcher
echo "📊 Stopping existing watcher..."
pkill -f "bb watcher" 2>/dev/null || true
sleep 2

# Start new watcher
echo "📊 Starting watcher with auto-discovery..."
cd "$(dirname "$0")/.."
bb watcher &

echo "✅ Watcher restarted successfully"
echo "📋 Check logs: tail -f watcher.log"
