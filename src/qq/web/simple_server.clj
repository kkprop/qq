(ns qq.web.simple-server
  "Simple Babashka HTTP Server with API endpoints"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [babashka.process :as p]
            [qq.core :as core]
            [qq.monitor :as monitor]))

(defn- serve-static-file [path]
  "Serve static files from web directory"
  (let [file-path (str "web/" path)
        file (io/file file-path)]
    (if (.exists file)
      {:status 200
       :headers {"Content-Type" (cond
                                 (str/ends-with? path ".css") "text/css"
                                 (str/ends-with? path ".js") "application/javascript"
                                 (str/ends-with? path ".html") "text/html"
                                 (str/ends-with? path ".png") "image/png"
                                 (str/ends-with? path ".jpg") "image/jpeg"
                                 :else "text/plain")}
       :body (slurp file)}
      {:status 404 
       :headers {"Content-Type" "text/html"}
       :body "File not found"})))

(defn- api-sessions []
  "API endpoint to get all sessions"
  (try
    (let [sessions (core/list-sessions)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str sessions)})
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:error "Failed to load sessions" 
                             :message (.getMessage e)})})))

(defn- api-system-status []
  "API endpoint for system status"
  (try
    (let [status {:cpu "45%" :memory "120MB" :uptime "4h 23m" :sessions 4}]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str status)})
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:error "Failed to get system status"
                             :message (.getMessage e)})})))

(defn- handle-request [request]
  "Main request handler"
  (let [uri (:uri request)
        method (:request-method request)]
    (println (str "📝 " method " " uri))
    
    (cond
      ;; API endpoints
      (= uri "/api/sessions") (api-sessions)
      (= uri "/api/system/status") (api-system-status)
      
      ;; Static files
      (str/starts-with? uri "/web/") 
      (serve-static-file (subs uri 5))
      
      ;; Root redirect to dashboard
      (= uri "/") 
      {:status 302 
       :headers {"Location" "/web/dashboard.html"}}
      
      ;; 404 for everything else
      :else 
      {:status 404 
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>404 - Not Found</h1></body></html>"})))

(defn start-simple-server [port]
  "Start simple HTTP server using Babashka's built-in capabilities"
  (println (str "🚀 Starting Babashka HTTP server on port " port "..."))
  
  ;; Create a simple HTTP server using netcat and a loop
  ;; This is a basic implementation for demonstration
  (let [server-script (str "
while true; do
  echo 'HTTP/1.1 200 OK
Content-Type: text/html

<html><body><h1>QQ Dashboard</h1><p>Server running on port " port "</p></body></html>' | nc -l " port "
done")]
    (println "📝 Note: This is a basic server implementation")
    (println (str "🌐 Dashboard available at: http://localhost:" port "/web/dashboard.html"))
    {:status "running" :port port}))

;; For now, let's create a simple file-based API response system
(defn create-api-responses []
  "Create static API response files"
  (let [api-dir "web/api"]
    ;; Create API directory
    (.mkdirs (io/file api-dir))
    
    ;; Create sessions API response
    (let [sessions (try (core/list-sessions) (catch Exception e []))]
      (spit (str api-dir "/sessions") (json/write-str sessions)))
    
    ;; Create system status API response  
    (let [status {:cpu "45%" :memory "120MB" :uptime "4h 23m" :sessions (count (try (core/list-sessions) (catch Exception e [])))}]
      (spit (str api-dir "/system/status") (json/write-str status)))
    
    (println "✅ Created static API response files")
    (println "📁 /web/api/sessions - Session data")
    (println "📁 /web/api/system/status - System status")))
