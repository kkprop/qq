# Tmux Grouped Sessions for Independent Navigation

## Problem
When multiple terminals attach to the same tmux session, they all share the same current window. Switching windows in one terminal affects all other attached terminals.

## Solution: Grouped Sessions
Tmux grouped sessions share the same window list but allow independent current window selection.

## Implementation

### Basic Setup
```bash
# Create base session with Q
tmux new-session -d -s qq-base q chat

# Terminal 1: Create grouped client
tmux new-session -t qq-base -s qq-client1

# Terminal 2: Create grouped client  
tmux new-session -t qq-base -s qq-client2
```

### Enhanced bb q Implementation
```clojure
q-independent {:doc "Attach to Q with independent window navigation"
               :task (let [client-id (str "qq-client-" (System/currentTimeMillis))]
                       (babashka.process/shell ["tmux" "new-session" "-d" "-s" "qq-base" "q" "chat"])
                       (babashka.process/exec ["tmux" "new-session" "-t" "qq-base" "-s" client-id]))}
```

## Benefits

### Shared Features
- ✅ Same window list across all clients
- ✅ Shared window content and processes
- ✅ New windows created by any client appear for all
- ✅ Shared Q conversations and history

### Independent Features
- ✅ Each client can be on different current window
- ✅ Window switching doesn't affect other clients
- ✅ Independent navigation while sharing content

## Use Cases
- **Multi-monitor setup**: Different Q windows on different screens
- **Collaborative work**: Team members can focus on different aspects
- **Monitoring**: One terminal watches logs, another interacts with Q
- **Development workflow**: Code in one window, Q assistance in another

## Future Enhancement
Could be implemented as `bb q-independent` or enhanced `bb q --independent` flag when needed.

---
*Documented: 2025-08-29 - Tmux grouped sessions insight for independent multi-client navigation*
