(ns qq.web.server
  "QQ Web Interface Server - Simple Babashka HTTP Server"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [babashka.process :as p]
            [qq.core :as core]
            [qq.monitor :as monitor]
            [qq.tmux :as tmux]
            [qq.session.manager :as session-mgr]))  ; Add real session management

;; Server configuration
(def ^:private DEFAULT-PORT 9090)
(def ^:private STATIC-DIR "web/static")

;; Simple HTTP server using Babashka's process capabilities
(defn- start-simple-server [port]
  "Start a simple HTTP server using Python's built-in server"
  (let [server-process (p/process ["python3" "-m" "http.server" (str port)]
                                 {:dir "."
                                  :out :inherit
                                  :err :inherit})]
    (println (str "🚀 Simple HTTP server started on port " port))
    (println (str "📁 Serving files from current directory"))
    (println (str "🌐 Open: http://localhost:" port "/web/dashboard.html"))
    server-process))

;; Create a simple static HTML file for our dashboard
(defn- create-dashboard-html []
  "Create a simple dashboard HTML file"
  (let [sessions (try (core/list-sessions) (catch Exception e []))
        session-count (count sessions)
        html-content (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>🤖 QQ Dashboard</title>
    <link rel=\"stylesheet\" href=\"static/css/dashboard.css\">
</head>
<body>
    <div class=\"container\">
        <header class=\"header\">
            <h1>🤖 QQ Dashboard</h1>
            <div class=\"header-info\">
                <span class=\"session-count\">Sessions: " session-count "</span>
                <span class=\"status\">🟢 Online</span>
            </div>
        </header>
        
        <main class=\"main-content\">
            <section class=\"sessions-section\">
                <h2>Active Sessions</h2>
                <div class=\"sessions-grid\">
                    " (if (empty? sessions)
                        "<div class=\"empty-state\">
                           <h3>No Active Sessions</h3>
                           <p>Create your first Q session to get started!</p>
                           <button class=\"btn btn-success\" onclick=\"createSession()\">Create Session</button>
                         </div>"
                        (str/join "\n" 
                          (map (fn [session]
                                 (str "<div class=\"session-card\">
                                         <div class=\"session-header\">
                                             <h3>" (get session :name "Unknown") "</h3>
                                             <span class=\"session-status\">🟢</span>
                                         </div>
                                         <div class=\"session-info\">
                                             <p>Messages: " (get session :message-count 0) "</p>
                                             <p>Last activity: " (get session :last-activity "Unknown") "</p>
                                         </div>
                                         <div class=\"session-actions\">
                                             <button class=\"btn btn-primary\" onclick=\"alert('Viewing session: " (get session :name "") "')\">View</button>
                                             <button class=\"btn btn-secondary\" onclick=\"alert('Controlling session: " (get session :name "") "')\">Control</button>
                                         </div>
                                       </div>"))
                               sessions))) "
                </div>
                
                <div class=\"actions\">
                    <button class=\"btn btn-success\" onclick=\"createSession()\">Create New Session</button>
                    <button class=\"btn btn-info\" onclick=\"location.reload()\">Refresh</button>
                    <button class=\"btn btn-primary\" onclick=\"window.open('/web/terminal.html', '_blank')\">Open Terminal</button>
                </div>
            </section>
            
            <aside class=\"system-info\">
                <h2>System Status</h2>
                <div class=\"system-stats\">
                    <div class=\"stat\">
                        <span class=\"stat-label\">CPU Usage</span>
                        <span class=\"stat-value\">--</span>
                    </div>
                    <div class=\"stat\">
                        <span class=\"stat-label\">Memory</span>
                        <span class=\"stat-value\">--</span>
                    </div>
                    <div class=\"stat\">
                        <span class=\"stat-label\">Sessions</span>
                        <span class=\"stat-value\">" session-count "</span>
                    </div>
                </div>
            </aside>
        </main>
    </div>
    
    <script src=\"static/js/dashboard.js\"></script>
</body>
</html>")]
    (spit "web/dashboard.html" html-content)
    (println "✅ Dashboard HTML created: web/dashboard.html")))

;; Server management functions
(def server-process (atom nil))

(defn start-server 
  "Start the QQ web server"
  ([] (start-server {:port DEFAULT-PORT}))
  ([{:keys [port] :or {port DEFAULT-PORT}}]
   (if @server-process
     (println "🌐 Server already running")
     (do
       (println "🏗️ Preparing web dashboard...")
       (create-dashboard-html)
       (println (str "🚀 Starting web server on port " port "..."))
       (let [process (start-simple-server port)]
         (reset! server-process process)
         (println "✅ QQ Web Dashboard is ready!")
         (println (str "🌐 Open: http://localhost:" port "/web/dashboard.html"))
         process)))))

(defn stop-server
  "Stop the QQ web server"
  []
  (if @server-process
    (do
      (println "🛑 Stopping web server...")
      (p/destroy @server-process)
      (reset! server-process nil)
      (println "✅ Server stopped"))
    (println "ℹ️ Server is not running")))

(defn server-status
  "Get current server status"
  []
  (if @server-process
    "🟢 Server running"
    "🔴 Server not running"))

(defn start-dev-server
  "Start server in development mode"
  [{:keys [port] :or {port DEFAULT-PORT}}]
  (println "🔧 Starting QQ Web Server in development mode...")
  (start-server {:port port}))

;; Helper function to refresh dashboard
(defn refresh-dashboard
  "Refresh the dashboard HTML with latest data"
  []
  (println "🔄 Refreshing dashboard...")
  (create-dashboard-html)
  (println "✅ Dashboard refreshed"))
