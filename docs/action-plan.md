# QQ Next Actions Plan

*Based on user feedback and current project status - 2025-07-27*

## 🎯 Phase 1: Core Functionality (Next 1-2 weeks)

### **1.1 Default Window Implementation** 
**Priority**: 🔥 High - Major UX improvement

**Decisions Made**:
- Session ID: `"default"`
- Auto-creation: On first `bb ask`
- Context: Timeline summary of Q&A interactions
- Session limits: Counts toward 21 session limit

**Implementation Tasks**:
- [ ] Create `ensure-default-session` function in `qq.session`
- [ ] Update `ask` command to auto-create default if no current session
- [ ] Implement timeline-based context generation for default
- [ ] Add default session marker in `bb list` output
- [ ] Test default session creation and usage

**Files to Modify**:
- `src/qq/core.clj` - Update ask command logic
- `src/qq/session.clj` - Add default session handling
- `src/qq/timeline.clj` - New module for Q&A timeline tracking

### **1.2 Q&A Timeline Data Organization**
**Priority**: 🔥 High - Foundation for default context

**Design Question**: How to organize timeline data for Q&A interactions?

**Proposed Structure**:
```
~/.knock/qq/sessions/{session-id}/
├── metadata.json          # Session info
├── timeline.json          # Q&A interaction log
└── tmux.log              # Raw tmux output cache
```

**Timeline JSON Format**:
```json
{
  "session_id": "default",
  "interactions": [
    {
      "timestamp": "2025-07-27T12:00:00Z",
      "type": "question",
      "content": "What causes Lambda cold starts?",
      "user": "human"
    },
    {
      "timestamp": "2025-07-27T12:00:15Z", 
      "type": "answer",
      "content": "Lambda cold starts are caused by...",
      "user": "q",
      "response_time_ms": 15000
    }
  ],
  "summary": "Discussion about AWS Lambda performance optimization",
  "last_updated": "2025-07-27T12:00:15Z"
}
```

**Implementation Tasks**:
- [ ] Create `qq.timeline` module for Q&A logging
- [ ] Implement interaction capture in tmux integration
- [ ] Add timeline summarization for default context
- [ ] Create timeline query functions for context generation

### **1.3 Question/Answer System with Async Support**
**Priority**: 🔥 High - Core functionality

**Decisions Made**:
- Default behavior: Synchronous (`bb ask`)
- Async functions: Use `!` suffix (`ask!`, `create-session!`)
- Leverage Clojure async protocols

**Implementation Tasks**:
- [ ] Implement synchronous `ask` function (current session or default)
- [ ] Implement asynchronous `ask!` function returning promise
- [ ] Add proper response handling and streaming support
- [ ] Integrate with timeline logging for all Q&A interactions
- [ ] Test both sync and async question flows

**Files to Modify**:
- `src/qq/core.clj` - Add ask and ask! functions
- `src/qq/tmux.clj` - Improve response handling
- `bb.edn` - Add ask! task

## 🔧 Phase 2: System Reliability (Next 2-3 weeks)

### **2.1 Naming Service Debug and Fix**
**Priority**: 🟡 Medium - Fix broken functionality

**Investigation Tasks**:
- [ ] Debug why naming service returns empty strings
- [ ] Test naming service tmux session manually
- [ ] Improve naming prompts for better results
- [ ] Implement better fallback strategy (timestamp + context keywords)
- [ ] Add logging to naming service for debugging

**Files to Modify**:
- `src/qq/naming.clj` - Debug and improve naming logic
- Add debug logging and better error handling

### **2.2 Context Module Syntax Fix**
**Priority**: 🟡 Medium - Enable advanced features

**Tasks**:
- [ ] Fix `extract-themes` function ordering issue completely
- [ ] Test context summarization in isolation
- [ ] Re-enable context tracking in core.clj
- [ ] Integrate context updates with timeline data

**Files to Modify**:
- `src/qq/context.clj` - Fix syntax issues
- `src/qq/core.clj` - Re-enable context import

### **2.3 Tmux API Exploration for Async Output**
**Priority**: 🟡 Medium - Performance improvement

**Research Tasks**:
- [ ] Explore tmux API for async output capture
- [ ] Investigate tmux hooks and event system
- [ ] Implement incremental output caching
- [ ] Test async output capture with multiple sessions

**Files to Modify**:
- `src/qq/tmux.clj` - Implement async output capture
- Possibly create `src/qq/tmux-async.clj` for advanced features

## 📊 Phase 3: Monitoring and Performance (Next 3-4 weeks)

### **3.1 Global Monitoring Utility**
**Priority**: 🟢 Nice-to-have - System health

**Decisions Made**:
- Monitor each tmux window's CPU and memory every 15 seconds
- Track resource usage and warn about performance issues

**Implementation Tasks**:
- [ ] Create `qq.monitor` module for resource monitoring
- [ ] Implement tmux session resource tracking
- [ ] Add monitoring daemon that runs in background
- [ ] Create monitoring dashboard/status command
- [ ] Add alerts for high resource usage

**Files to Create**:
- `src/qq/monitor.clj` - Resource monitoring
- `src/qq/daemon.clj` - Background monitoring process

### **3.2 Storage Optimization**
**Priority**: 🟢 Nice-to-have - Scalability

**Decisions Made**:
- No limit on conversation history storage (user's choice)
- Focus on efficient storage and retrieval

**Tasks**:
- [ ] Optimize JSON storage for large conversation histories
- [ ] Implement conversation history compression
- [ ] Add storage usage reporting
- [ ] Create cleanup utilities (optional for users)

## 🎯 Implementation Timeline

### **Week 1-2: Core Functionality**
```
Day 1-2:   Default window implementation
Day 3-4:   Q&A timeline data structure
Day 5-7:   Question/Answer system with sync/async
Day 8-10:  Integration testing and bug fixes
```

### **Week 3-4: System Reliability** 
```
Day 11-12: Naming service debugging
Day 13-14: Context module fixes
Day 15-17: Tmux async API exploration
Day 18-20: Integration and testing
```

### **Week 5-6: Monitoring and Polish**
```
Day 21-23: Global monitoring utility
Day 24-25: Storage optimization
Day 26-28: Performance testing and optimization
```

## 🔄 Development Process

### **Daily Workflow**:
1. **Morning**: Review timeline, plan day's tasks
2. **Implementation**: Follow TDD approach where possible
3. **Testing**: Test in tmux environment after each feature
4. **Evening**: Update timeline with progress and decisions

### **Weekly Reviews**:
- **Monday**: Plan week's priorities from action plan
- **Friday**: Review progress, update action plan if needed
- **Document**: Update timeline with major decisions and implementations

## 📋 Success Criteria

### **Phase 1 Complete When**:
- [ ] `bb ask "question"` works immediately (creates default session)
- [ ] Default session has meaningful context from timeline
- [ ] Both sync and async question asking work
- [ ] Q&A interactions are logged and accessible

### **Phase 2 Complete When**:
- [ ] Naming service generates proper session names
- [ ] Context summarization works without errors
- [ ] Tmux output capture is efficient and reliable

### **Phase 3 Complete When**:
- [ ] Resource monitoring provides useful insights
- [ ] System handles many sessions without performance issues
- [ ] Storage is optimized for large conversation histories

## 🤔 Open Questions for Next Discussion

1. **Timeline Data**: Should we store raw tmux output or parsed Q&A pairs?
2. **Monitoring Scope**: What specific metrics are most important to track?
3. **Default Context**: How much timeline history should inform default context?
4. **Async UI**: How should async operations be presented to users?

---

*This action plan provides a clear roadmap for the next 6 weeks of QQ development, prioritizing user experience improvements and system reliability.*
