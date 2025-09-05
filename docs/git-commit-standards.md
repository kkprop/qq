# Git Commit Standards (规范)

## 🎯 **Core Principles**

### **1. Security First**
- ✅ **Always check `git status` before committing**
- ✅ **Never commit sensitive data** (tokens, passwords, personal info)
- ✅ **Use `.gitignore` proactively** for temporary files
- ❌ **Avoid `git add -A`** without careful review

### **2. Commit Content Rules**
- ✅ **Commit working code only** - ensure it compiles/runs
- ✅ **One logical change per commit** - atomic commits
- ✅ **Include all related files** for the feature
- ❌ **No temporary/draft files** (`drafts/`, `tmp/`, `.DS_Store`)

### **3. Pre-Commit Checklist**
```bash
# Always run before committing:
git status                    # Review what's being committed
bb -e "(load-file \"...\")"   # Lint/syntax check
git diff --cached             # Review actual changes
```

## 📝 **Commit Message Format**

### **Structure**
```
<type>: <brief description>

<detailed explanation>
- Key changes
- Technical details
- Impact/benefits
```

### **Types**
- `feat:` - New feature implementation
- `fix:` - Bug fixes
- `refactor:` - Code restructuring without behavior change
- `docs:` - Documentation updates
- `test:` - Test additions/modifications
- `chore:` - Maintenance tasks (dependencies, build)

### **Good Examples**
```
feat: Implement hierarchical Roam integration with UID capture

✅ Major Features:
- H1 as root parent, H2 as children, H3+ content as grandchildren
- UID capture via timestamp + content matching queries
- Proper parent-child relationships in Roam blocks

✅ Technical Implementation:
- parse-hierarchical-chunks: H1/H2/H3+ structure parsing
- write-with-uid-capture: Timestamp-based UID retrieval
- Smart rate limiting optimization

Target achieved: Full evangelism content in proper Roam hierarchy!
```

### **Bad Examples**
```
❌ "fix stuff"
❌ "wip"
❌ "update files"
❌ "commit before lunch"
```

## 🚨 **Security Incidents Protocol**

### **If Sensitive Data Committed**
1. **STOP** - Don't push if not pushed yet
2. **Assess damage** - What was exposed?
3. **If already pushed to public repo:**
   - Data is **permanently in git history**
   - Consider rotating any exposed credentials
   - Document the incident
   - Learn from it

### **Prevention**
- Use `.gitignore` for:
  ```
  drafts/
  tmp/
  *.log
  .env
  config.edn
  *-secrets.*
  ```

## 🔄 **Workflow Best Practices**

### **Before Committing**
```bash
# 1. Review changes
git status
git diff

# 2. Stage selectively (avoid git add -A)
git add src/specific/file.clj
git add docs/new-doc.md

# 3. Lint/test
bb lint
bb test

# 4. Review staged changes
git diff --cached

# 5. Commit with good message
git commit -m "feat: implement feature X

- Technical detail 1
- Technical detail 2
- Impact/benefit"
```

### **After Committing**
```bash
# Review the commit
git show HEAD

# If mistake found BEFORE pushing:
git reset --soft HEAD~1  # Undo commit, keep changes
# Fix and recommit

# If already pushed:
# Accept the mistake is permanent in history
```

## 📋 **File Categories**

### **✅ Always Commit**
- Source code (`src/`)
- Documentation (`docs/`, `README.md`)
- Configuration (`bb.edn`, `.gitignore`)
- Tests (`test/`)

### **❌ Never Commit**
- Temporary files (`drafts/`, `tmp/`)
- Secrets (`.env`, `config.edn`)
- Generated files (`target/`, `node_modules/`)
- Personal files (`.DS_Store`, `Thumbs.db`)
- IDE files (`.vscode/`, `.idea/`)

### **🤔 Consider Carefully**
- Large files (>1MB)
- Binary files
- Log files
- Cache files

## 🎯 **QQ Project Specific**

### **Sensitive Areas**
- `config.edn` - Contains API tokens
- `drafts/` - May contain personal content
- Any files with "secret", "token", "key" in name

### **Safe to Commit**
- `src/qq/` - All source code
- `docs/` - Documentation
- `bb.edn` - Build configuration
- `README.md` - Project documentation

## 🔍 **Recovery Commands**

### **Undo Last Commit (Not Pushed)**
```bash
git reset --soft HEAD~1    # Keep changes, undo commit
git reset --hard HEAD~1    # Discard changes and commit
```

### **Remove File from Last Commit (Not Pushed)**
```bash
git reset --soft HEAD~1
git reset HEAD file-to-remove.txt
git commit -c ORIG_HEAD
```

### **If Already Pushed**
- **Accept it's permanent** in git history
- Focus on preventing future incidents
- Rotate any exposed credentials

---

**Remember: Git history is immutable in public repositories. Prevention is the only real protection! 🔒**
