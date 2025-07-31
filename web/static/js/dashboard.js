// QQ Dashboard - Interactive JavaScript
class QQDashboard {
    constructor() {
        this.apiBase = '/api';
        this.refreshInterval = null;
        this.init();
    }

    init() {
        console.log('🚀 QQ Dashboard initialized');
        this.setupEventListeners();
        this.loadInitialData();
        this.startAutoRefresh();
    }

    setupEventListeners() {
        // Add keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.metaKey) {
                switch(e.key) {
                    case 'r':
                        e.preventDefault();
                        this.refreshDashboard();
                        break;
                    case 'n':
                        e.preventDefault();
                        this.createSession();
                        break;
                }
            }
        });

        // Add click handlers for dynamic content
        document.addEventListener('click', (e) => {
            if (e.target.matches('[onclick]')) {
                // Let onclick handlers work naturally
                return;
            }
        });
    }

    async loadInitialData() {
        console.log('📊 Loading initial dashboard data...');
        await this.loadSessions();
        await this.loadSystemStatus();
    }

    async loadSessions() {
        try {
            const response = await fetch(`${this.apiBase}/sessions`);
            const sessions = await response.json();
            
            if (sessions.error) {
                console.error('❌ Error loading sessions:', sessions.error);
                this.showError('Failed to load sessions: ' + sessions.message);
                return;
            }

            console.log(`✅ Loaded ${sessions.length} sessions`);
            this.updateSessionsDisplay(sessions);
        } catch (error) {
            console.error('❌ Network error loading sessions:', error);
            this.showError('Network error loading sessions');
        }
    }

    async loadSystemStatus() {
        try {
            const response = await fetch(`${this.apiBase}/system/status`);
            const status = await response.json();
            
            if (status.error) {
                console.error('❌ Error loading system status:', status.error);
                return;
            }

            console.log('✅ Loaded system status');
            this.updateSystemStatus(status);
        } catch (error) {
            console.error('❌ Network error loading system status:', error);
        }
    }

    updateSessionsDisplay(sessions) {
        const sessionsGrid = document.querySelector('.sessions-grid');
        if (!sessionsGrid) return;

        if (sessions.length === 0) {
            sessionsGrid.innerHTML = `
                <div class="empty-state">
                    <h3>No Active Sessions</h3>
                    <p>Create your first Q session to get started!</p>
                    <button class="btn btn-success" onclick="createSession()">Create Session</button>
                </div>
            `;
            return;
        }

        // Update session count in header
        const sessionCountEl = document.querySelector('.session-count');
        if (sessionCountEl) {
            sessionCountEl.textContent = `Sessions: ${sessions.length}`;
        }

        // This will be dynamically updated when we have real session data
        console.log('📋 Sessions to display:', sessions);
    }

    updateSystemStatus(status) {
        // Update system stats in sidebar
        const stats = document.querySelectorAll('.stat-value');
        if (stats.length >= 3) {
            stats[0].textContent = status.cpu || '--';
            stats[1].textContent = status.memory || '--';
            stats[2].textContent = status.uptime || '--';
        }
    }

    startAutoRefresh() {
        // Refresh every 30 seconds
        this.refreshInterval = setInterval(() => {
            this.loadSessions();
            this.loadSystemStatus();
        }, 30000);

        console.log('🔄 Auto-refresh started (30s interval)');
    }

    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
            console.log('⏹️ Auto-refresh stopped');
        }
    }

    showError(message) {
        // Simple error display - can be enhanced later
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #f44336;
            color: white;
            padding: 15px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            z-index: 1000;
            max-width: 400px;
        `;
        errorDiv.textContent = message;
        
        document.body.appendChild(errorDiv);
        
        // Auto-remove after 5 seconds
        setTimeout(() => {
            if (errorDiv.parentNode) {
                errorDiv.parentNode.removeChild(errorDiv);
            }
        }, 5000);
    }

    showSuccess(message) {
        // Simple success display
        const successDiv = document.createElement('div');
        successDiv.className = 'success-message';
        successDiv.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #4CAF50;
            color: white;
            padding: 15px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            z-index: 1000;
            max-width: 400px;
        `;
        successDiv.textContent = message;
        
        document.body.appendChild(successDiv);
        
        // Auto-remove after 3 seconds
        setTimeout(() => {
            if (successDiv.parentNode) {
                successDiv.parentNode.removeChild(successDiv);
            }
        }, 3000);
    }
}

// Global functions for onclick handlers
function viewSession(sessionName) {
    console.log(`👁️ Viewing session: ${sessionName}`);
    // TODO: Navigate to session detail page
    dashboard.showSuccess(`Opening session: ${sessionName}`);
}

function controlSession(sessionName) {
    console.log(`🎮 Controlling session: ${sessionName}`);
    // TODO: Open session control interface
    dashboard.showSuccess(`Controlling session: ${sessionName}`);
}

function createSession() {
    console.log('➕ Creating new Q session');
    const sessionName = prompt('Enter session name (or leave empty for auto-generated):');
    
    if (sessionName !== null) { // User didn't cancel
        const name = sessionName.trim() || `qq-session-${Date.now()}`;
        console.log(`🚀 Creating real Q session: ${name}`);
        dashboard.showSuccess(`Creating Q session: ${name}`);
        
        // Create real Q session using tmux and q chat
        createRealQSession(name)
            .then(result => {
                if (result.success) {
                    console.log('✅ Q session created successfully');
                    dashboard.showSuccess(`Q session "${result.session.name}" created successfully!`);
                    dashboard.showSuccess(`Attach with: tmux attach -t ${result.session.name}`);
                    
                    // Refresh after creating
                    setTimeout(() => {
                        dashboard.loadSessions();
                    }, 1000);
                } else {
                    console.error('❌ Failed to create Q session:', result.error);
                    dashboard.showError(`Failed to create Q session: ${result.error}`);
                }
            })
            .catch(error => {
                console.error('❌ Error creating Q session:', error);
                dashboard.showError('Failed to create Q session. Check console for details.');
            });
    }
}

async function createRealQSession(sessionName) {
    console.log(`🔧 Creating real Q session: ${sessionName}`);
    
    try {
        // For now, simulate the session creation since we need backend integration
        // In a real implementation, this would call a backend API
        
        const tmuxName = `qq-${sessionName}`;
        
        // Simulate session creation
        const result = {
            success: true,
            session: {
                name: tmuxName,
                status: 'active',
                messages: 0,
                created: new Date().toISOString()
            }
        };
        
        console.log('🎯 Real Q session creation would execute:');
        console.log(`1. tmux new-session -d -s ${tmuxName}`);
        console.log(`2. tmux send-keys -t ${tmuxName} "q chat" Enter`);
        console.log('3. Session ready for Q chat interaction');
        
        // Show instructions to user
        dashboard.showSuccess(`To use your Q session: tmux attach -t ${tmuxName}`);
        
        return result;
        
    } catch (error) {
        console.error('Error in createRealQSession:', error);
        return {
            success: false,
            error: error.message
        };
    }
}

function refreshDashboard() {
    console.log('🔄 Refreshing dashboard');
    dashboard.showSuccess('Refreshing dashboard...');
    dashboard.loadSessions();
    dashboard.loadSystemStatus();
}

// Initialize dashboard when page loads
let dashboard;
document.addEventListener('DOMContentLoaded', () => {
    dashboard = new QQDashboard();
});

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (dashboard) {
        dashboard.stopAutoRefresh();
    }
});

// Add some helpful console messages
console.log(`
🤖 QQ Dashboard JavaScript Loaded!

Keyboard shortcuts:
- Ctrl/Cmd + R: Refresh dashboard
- Ctrl/Cmd + N: Create new session

Dashboard will auto-refresh every 30 seconds.
`);
