# 🚀 Smart Scroll Hooking System - Complete Guide

## 🎯 Overview

This document describes the **Smart Scroll Hooking System** that enables continuous, seamless scrolling in web-based terminal applications, mimicking the behavior of native tmux sessions. The system provides **real tmux-like scrolling experience** with incremental history loading.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    SMART SCROLL HOOKING                     │
├─────────────────────────────────────────────────────────────┤
│  Browser (xterm.js)     │    WebSocket Server (Clojure)    │
│                         │                                   │
│  ┌─────────────────┐   │    ┌─────────────────────────────┐ │
│  │ .xterm-viewport │◄──┼────┤ tmux history capture       │ │
│  │ (scroll events) │   │    │ (capture-full-tmux-history) │ │
│  └─────────────────┘   │    └─────────────────────────────┘ │
│           │             │                    │               │
│  ┌─────────────────┐   │    ┌─────────────────────────────┐ │
│  │ historyBuffer[] │◄──┼────┤ incremental loading         │ │
│  │ (continuous)    │   │    │ (load-incremental-history)  │ │
│  └─────────────────┘   │    └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Core Components

### 1. **Scroll Detection Engine**

**Target Element**: `.xterm-viewport` (NOT `.terminal-wrapper`)

```javascript
function setupScrollDetection() {
    // 🎯 KEY INSIGHT: Target xterm.js viewport, not wrapper
    const xtermViewport = document.querySelector('.xterm-viewport');
    
    if (xtermViewport) {
        xtermViewport.addEventListener('scroll', function(e) {
            const scrollTop = xtermViewport.scrollTop;
            const scrollHeight = xtermViewport.scrollHeight;
            
            // Trigger loading when near top
            if (scrollTop < 200 && hasMoreHistory && !isLoading) {
                loadMoreHistoryForContinuous();
            }
        });
    }
}
```

**🔍 Why `.xterm-viewport`?**
- ✅ **Actual scrollable element** in xterm.js
- ✅ **Receives real scroll events** from user interaction
- ✅ **Has proper scrollHeight/scrollTop** properties
- ❌ `.terminal-wrapper` is just a container, not scrollable

### 2. **Continuous Buffer System**

**Core Data Structure**:
```javascript
let historyBuffer = [];        // All loaded content in order
let loadedHistoryLines = 0;    // Track loaded history count
let totalHistoryLines = 0;     // Total available history
let isLoadingHistory = false;  // Prevent concurrent loading
```

**Buffer Management**:
```javascript
function prependHistoryToBuffer(newHistoryLines) {
    // Add older content to beginning
    historyBuffer = newHistoryLines.concat(historyBuffer);
    loadedHistoryLines += newHistoryLines.length;
    
    // Rebuild continuous view
    buildContinuousBuffer();
    
    // Maintain scroll position
    maintainScrollPosition(newHistoryLines.length);
}
```

### 3. **Server-Side History Management**

**Clojure Implementation**:
```clojure
(defn load-incremental-history
  "Load more history incrementally when user scrolls up"
  [client-socket session-name offset limit]
  (let [full-content (capture-full-tmux-history session-name)
        lines (str/split-lines full-content)
        start-index (max 0 (- total-lines offset limit))
        end-index (- total-lines offset)
        history-lines (subvec (vec lines) start-index end-index)]
    
    ;; Send incremental history chunk
    (send-websocket-frame output-stream 
      {:type "tmux-incremental-history"
       :content (str/join "\n" history-lines)
       :hasMore (> start-index 0)})))
```

## 🎯 Smart Hooking Strategy

### **Phase 1: Initial Load**
```javascript
case 'tmux-current-page':
    // Initialize with last 50 lines (current page)
    const currentLines = data.content.split('\n');
    historyBuffer = currentLines;
    loadedHistoryLines = data.currentPageLines;
    totalHistoryLines = data.totalLines;
    
    // Build initial continuous buffer
    buildContinuousBuffer();
    
    // Setup scroll detection
    setTimeout(setupScrollDetection, 1000);
    
    // Auto-scroll to bottom (most recent)
    scrollToBottom();
```

### **Phase 2: Incremental Loading**
```javascript
case 'tmux-incremental-history':
    // Add older content seamlessly
    const newHistoryLines = data.content.split('\n');
    prependHistoryToBuffer(newHistoryLines);
    
    // Maintain user's scroll position
    maintainScrollPosition();
    
    isLoadingHistory = false;
```

### **Phase 3: Real-time Updates**
```javascript
case 'tmux-realtime':
    // Add new content to buffer
    const newLines = data.content.split('\n');
    historyBuffer = historyBuffer.concat(newLines);
    
    // Display new content
    terminal.write(data.content + '\r\n');
    
    // Auto-scroll if user is at bottom
    autoScrollIfAtBottom();
```

## 🔄 Scroll Position Management

### **Maintain Position During History Loading**
```javascript
function maintainScrollPosition(newLinesCount) {
    setTimeout(() => {
        const xtermViewport = document.querySelector('.xterm-viewport');
        if (xtermViewport) {
            // Scroll down to maintain relative position
            xtermViewport.scrollTop = newLinesCount * 20; // ~20px per line
        }
    }, 100);
}
```

### **Auto-scroll for New Content**
```javascript
function autoScrollIfAtBottom() {
    const xtermViewport = document.querySelector('.xterm-viewport');
    if (xtermViewport) {
        const isNearBottom = xtermViewport.scrollTop + xtermViewport.clientHeight 
                           >= xtermViewport.scrollHeight - 100;
        if (isNearBottom) {
            xtermViewport.scrollTop = xtermViewport.scrollHeight;
        }
    }
}
```

## 🎨 User Experience Features

### **1. Seamless History Loading**
- ✅ **No jarring replacements** - content prepends smoothly
- ✅ **Position maintained** - user stays at same relative position
- ✅ **Loading indicators** - clear feedback during loading

### **2. Smart Auto-scroll**
- ✅ **Auto-scroll to bottom** for new real-time content
- ✅ **Preserve position** when user is reading history
- ✅ **Natural behavior** like native terminal applications

### **3. Performance Optimization**
- ✅ **Chunked loading** - 50 lines at a time
- ✅ **Lazy loading** - only load when needed
- ✅ **Concurrent protection** - prevent multiple simultaneous loads

## 🔍 Debugging & Troubleshooting

### **Common Issues & Solutions**

**1. Scroll Events Not Firing**
```javascript
// ❌ Wrong: Targeting wrapper
const wrapper = document.querySelector('.terminal-wrapper');

// ✅ Correct: Target xterm viewport
const viewport = document.querySelector('.xterm-viewport');
```

**2. Content Replacement Instead of Prepending**
```javascript
// ❌ Wrong: Replace content
terminal.clear();
terminal.write(newContent);

// ✅ Correct: Prepend to buffer
historyBuffer = newHistoryLines.concat(historyBuffer);
buildContinuousBuffer();
```

**3. Lost Scroll Position**
```javascript
// ✅ Always maintain position after loading
function prependHistoryToBuffer(newLines) {
    // ... add content ...
    maintainScrollPosition(newLines.length);
}
```

### **Debug Console Messages**
```javascript
// Setup phase
"🔧 Setting up continuous scroll detection..."
"✅ Found xterm viewport for continuous scrolling"

// Scroll detection
"📜 Xterm scroll position: X / Y"
"🔄 Loading more history for continuous scrolling..."

// Buffer management
"📜 Rebuilt continuous buffer with X lines"
"📜 Added X history lines. Total: Y"
```

## 🚀 Implementation Checklist

### **Frontend (JavaScript)**
- [ ] Target `.xterm-viewport` for scroll events
- [ ] Implement `historyBuffer[]` for continuous content
- [ ] Add `prependHistoryToBuffer()` function
- [ ] Implement `maintainScrollPosition()` 
- [ ] Add auto-scroll for new content
- [ ] Setup proper debug logging

### **Backend (Server)**
- [ ] Implement `load-incremental-history` message handler
- [ ] Add `capture-full-tmux-history` function
- [ ] Implement chunked content delivery
- [ ] Add proper error handling
- [ ] Setup WebSocket message routing

### **Integration**
- [ ] Test scroll detection with real user interaction
- [ ] Verify incremental loading triggers correctly
- [ ] Test scroll position maintenance
- [ ] Validate auto-scroll behavior
- [ ] Performance test with large history

## 🎯 Key Success Factors

### **1. Element Targeting**
- 🎯 **Critical**: Use `.xterm-viewport`, not wrapper elements
- 🎯 **Why**: Only the viewport receives actual scroll events

### **2. Buffer Management**
- 🎯 **Strategy**: Continuous buffer, not chunked replacement
- 🎯 **Implementation**: Prepend older content, append new content

### **3. Position Maintenance**
- 🎯 **User Experience**: Maintain scroll position during loading
- 🎯 **Technical**: Calculate offset based on new content height

### **4. Performance**
- 🎯 **Loading**: Incremental chunks (50 lines)
- 🎯 **Concurrency**: Prevent multiple simultaneous loads
- 🎯 **Memory**: Efficient buffer management

## 🎉 Result: Real Tmux Experience

**Before**: Chunked loading with jarring content replacement
**After**: Seamless continuous scrolling like native tmux

**User Experience**:
- ✅ **Scroll up** → see older history load seamlessly
- ✅ **Scroll down** → see newer content naturally  
- ✅ **New content** → auto-scroll to follow updates
- ✅ **Position maintained** → no jarring jumps
- ✅ **Performance** → smooth, responsive scrolling

## 🔗 Related Files

- `web/terminal.html` - Frontend scroll hooking implementation
- `src/qq/terminal/websocket_server.clj` - Backend history management
- `AGGRESSIVE_MIRRORING_STATUS.md` - Overall system documentation

---

**🎯 This smart scroll hooking system transforms a web terminal into a native tmux-like experience with seamless, continuous scrolling! 🚀**
