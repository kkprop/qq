# 🤖 QQ Browser Testing Framework

Comprehensive browser automation and testing utilities for QQ Dashboard development and validation.

## 📋 Overview

The QQ Browser Testing Framework provides automated testing, screenshot capture, UI validation, and interactive debugging capabilities for the QQ Dashboard. Built on top of Etaoin WebDriver automation.

## 🎯 Key Features

### 📸 Screenshot Automation
- **Timestamped screenshots** with descriptive names
- **Automatic capture** during test phases
- **Error screenshots** for debugging failures
- **Organized storage** in `screenshots/` directory

### 🧪 Automated Testing Suites
- **Phase-based testing** for systematic validation
- **Comprehensive test coverage** of dashboard functionality
- **API endpoint testing** from browser context
- **UI element validation** and interaction testing

### 🖱️ JavaScript Injection
- **Prompt override** for automated input handling
- **Alert interception** to prevent test blocking
- **Test helper functions** injected into browser context
- **Console logging** enhancement for debugging

### 📊 Real-time Monitoring
- **Console log capture** and analysis
- **API response validation** 
- **Element existence checking**
- **Performance timing** measurements

## 🚀 Quick Start

### Basic Usage

```bash
# Quick validation test (fast)
bb quick-dashboard-test

# Interactive test with manual inspection
bb interactive-dashboard-test

# Full comprehensive test suite
bb comprehensive-dashboard-test
```

### Programmatic Usage

```clojure
(require '[qq.browser.testing :as bt])

;; Run quick validation
(bt/quick-dashboard-test)

;; Capture screenshot
(let [driver (e/chrome {:headless false})]
  (bt/capture-screenshot driver "my-test"))

;; Test specific functionality
(bt/test-create-session-flow driver "test-session")
```

## 📚 API Reference

### Core Functions

#### `capture-screenshot [driver name]`
📸 Capture timestamped screenshot with descriptive name

**Args:**
- `driver` - Etaoin driver instance
- `name` - Descriptive name for the screenshot

**Returns:** String path to saved screenshot

**Example:**
```clojure
(capture-screenshot driver "dashboard-loaded")
;; Saves: screenshots/dashboard-loaded_2025-07-31_10-30-45.png
```

#### `inject-test-helpers [driver]`
🖱️ Inject JavaScript test helpers into the browser

Adds utility functions for automated testing:
- Prompt override for automated input
- Alert interception to prevent blocking
- Console logging helpers
- Test data injection

**Example:**
```clojure
(inject-test-helpers driver)
;; Now browser has window.testHelpers available
```

#### `validate-ui-elements [driver elements]`
👁️ Validate presence and visibility of UI elements

**Args:**
- `driver` - Etaoin driver instance  
- `elements` - Vector of CSS selectors to validate

**Returns:** Map of element -> boolean (exists/visible)

**Example:**
```clojure
(validate-ui-elements driver [".container" ".btn-success"])
;; => {".container" true, ".btn-success" true}
```

#### `test-api-endpoints [driver endpoints]`
📊 Test API endpoints from browser context

**Args:**
- `driver` - Etaoin driver instance
- `endpoints` - Vector of endpoint paths to test

**Returns:** Map of endpoint -> test results

**Example:**
```clojure
(test-api-endpoints driver ["/api/sessions" "/api/system/status"])
;; => {"/api/sessions" {:status 200, :ok true, ...}}
```

### Specialized Test Functions

#### `test-create-session-flow [driver session-name]`
🧪 Test the complete Create Session workflow

Tests the entire session creation process:
1. Button existence validation
2. JavaScript prompt handling
3. Session creation execution
4. Console log analysis
5. Screenshot capture

**Example:**
```clojure
(test-create-session-flow driver "my-test-session")
;; => {:success true, :session-name "my-test-session", ...}
```

#### `run-comprehensive-dashboard-test [driver]`
🎯 Run comprehensive dashboard functionality test

Executes full test suite:
- UI element validation
- Create Session workflow testing
- API endpoint validation
- Console log analysis
- Screenshot capture at each phase

**Example:**
```clojure
(run-comprehensive-dashboard-test)
;; Runs complete test suite with new browser instance
```

### Convenience Functions

#### `quick-dashboard-test []`
⚡ Quick dashboard functionality test

Fast validation of core dashboard features. Perfect for development and CI/CD.

**Features:**
- Core element validation
- Screenshot capture
- Fast execution (< 10 seconds)
- Pass/fail result

#### `interactive-dashboard-test []`
🎮 Interactive dashboard test with browser kept open

Comprehensive test with manual inspection capability.

**Features:**
- Full test suite execution
- Browser stays open for inspection
- Manual control after automated tests
- Perfect for debugging

## 🔧 Configuration

### Environment Variables

```bash
# Screenshot directory (default: screenshots)
export QQ_SCREENSHOT_DIR="my-screenshots"

# Dashboard URL (default: http://localhost:9090/web/dashboard.html)
export QQ_DASHBOARD_URL="http://localhost:8080/dashboard"

# Test timeout (default: 5000ms)
export QQ_TEST_TIMEOUT="10000"
```

### Browser Options

The framework uses Chrome by default with these options:
- **Non-headless mode** for visual debugging
- **Download directory** configured
- **Console logging** enabled
- **Popup blocking** disabled

## 📊 Test Results

### Success Indicators

✅ **Successful Test Results:**
```clojure
{:success true
 :test-type "comprehensive-dashboard"
 :timestamp #inst "2025-07-31T10:30:45.123Z"
 :ui-validation {".container" true, ".btn-success" true}
 :api-testing {"/api/sessions" {:status 200, :ok true}}
 :session-creation {:success true, :session-name "test-session"}
 :console-logs [...]}
```

❌ **Failed Test Results:**
```clojure
{:success false
 :error "Create Session button not found"
 :ui-validation {".container" true, ".btn-success" false}
 :timestamp #inst "2025-07-31T10:30:45.123Z"}
```

## 🐛 Debugging

### Common Issues

#### Browser Not Starting
```bash
# Check Chrome installation
which google-chrome

# Check chromedriver
which chromedriver
```

#### Dashboard Not Loading
```bash
# Verify web server is running
curl http://localhost:9090/web/dashboard.html

# Check server logs
bb web-server-logs
```

#### Screenshots Not Saving
```bash
# Create screenshots directory
mkdir -p screenshots

# Check permissions
ls -la screenshots/
```

### Debug Mode

Enable verbose logging:
```bash
# Set debug environment
export QQ_DEBUG=true
export QQ_LOG_LEVEL=debug

# Run tests with debug output
bb interactive-dashboard-test
```

## 🎯 Testing Phases

### Phase 1: Dashboard Functionality ✅
- Create Session button testing
- UI element validation  
- API endpoint verification
- Console log analysis

### Phase 2: End-to-End Session Management 🔄
- Real session creation validation
- Session lifecycle testing
- Multi-session management
- Session cleanup verification

### Phase 3: Q Chat Integration 🔄
- Q chat message sending
- Response validation
- Chat history testing
- Multi-conversation management

### Phase 4: System Monitoring 🔄
- Real-time metrics validation
- Performance monitoring
- Resource usage tracking
- Health check automation

### Phase 5: Automation & Testing 🔄
- Stress testing capabilities
- Load testing automation
- Performance benchmarking
- CI/CD integration

## 📈 Performance Metrics

### Typical Test Times

- **Quick Test:** ~5-10 seconds
- **Interactive Test:** ~30-60 seconds (+ manual time)
- **Comprehensive Test:** ~60-120 seconds
- **Phase 1 Test:** ~30-45 seconds

### Resource Usage

- **Memory:** ~100-200MB per browser instance
- **CPU:** Low during waiting, moderate during interaction
- **Disk:** ~1-5MB per screenshot
- **Network:** Minimal (local requests only)

## 🔮 Future Enhancements

### Planned Features

- **Parallel testing** across multiple browsers
- **Mobile responsive** testing capabilities
- **Performance profiling** integration
- **Visual regression** testing
- **Accessibility testing** automation
- **Cross-browser compatibility** validation

### Integration Opportunities

- **CI/CD pipeline** integration
- **Slack/Discord** notification hooks
- **Test report generation** (HTML/PDF)
- **Metrics dashboard** for test results
- **Automated regression** detection

## 🤝 Contributing

### Adding New Tests

1. Create test function in `qq.browser.testing`
2. Add documentation with examples
3. Include error handling and screenshots
4. Add bb.edn task entry
5. Update this documentation

### Test Function Template

```clojure
(defn test-new-feature
  "🧪 Test description
  
  Args:
    driver - Etaoin driver instance
    
  Returns:
    Test results map
    
  Example:
    (test-new-feature driver)"
  [driver]
  (println "🧪 Testing new feature...")
  
  (try
    ;; Test implementation
    (capture-screenshot driver "new-feature-test")
    
    {:success true
     :feature "new-feature"
     :timestamp (java.time.Instant/now)}
    
    (catch Exception e
      (capture-screenshot driver "new-feature-error")
      {:success false
       :error (.getMessage e)})))
```

---

**🎉 Happy Testing! The QQ Browser Testing Framework makes dashboard validation fast, reliable, and comprehensive!**
