# Shell Completion Features - Backlog

## Implemented ✅
- **Bash Tab Completion**: `bb shell-completion install`
- **Directory-aware**: Only completes when bb.edn exists
- **Dynamic task detection**: Reads current project's tasks

## Backlog 🔮

### Gray Text Inline Suggestions
**Goal**: Show gray preview text as user types (like fish shell autosuggestions)

#### Fish Shell (Native Support)
```fish
# ~/.config/fish/completions/bb.fish
complete -c bb -f -a "(bb tasks 2>/dev/null | grep -E '^[a-z]' | awk '{print \$1}')"
```

#### Zsh with Autosuggestions Plugin
```zsh
# Install zsh-autosuggestions
git clone https://github.com/zsh-users/zsh-autosuggestions ~/.zsh/zsh-autosuggestions

# ~/.zshrc
source ~/.zsh/zsh-autosuggestions/zsh-autosuggestions.zsh
```

#### Implementation Plan
1. **Detect shell type** in `qq.bootstrap`
2. **Fish setup**: Create fish completion file
3. **Zsh setup**: Install autosuggestions plugin + completion
4. **Bash limitation**: Explain gray text not available
5. **Universal installer**: `bb shell-completion install-advanced`

### Advanced Features
- **Context-aware suggestions**: Different completions based on task
- **Argument completion**: Complete task-specific arguments
- **Multi-project support**: Handle multiple bb.edn projects
- **Shell detection**: Auto-detect user's shell and setup accordingly

---
*Documented: 2025-08-29 - Shell completion roadmap and gray text feature backlog*
