(ns qq.roam.test
  (:require [clojure.edn :as edn]
            [qq.roam.client :as client]
            [qq.roam.blocks :as blocks]
            [qq.config :as config]))

(defn test-config []
  (println "🔧 Testing config...")
  (let [graphs (config/get-config :roam-graphs)
        default (config/get-config :default-graph)]
    (println "Available graphs:" (keys graphs))
    (println "Default graph:" default)
    (println "Lisp config:" (get graphs :lisp))))

(defn test-roam-write []
  (println "🧪 Testing Roam write to lisp graph")
  (try
    (let [result (client/roam-write :lisp "QQ Test Block - Hello from QQ! 🤖")]
      (println "✅ Success! Block created:")
      (println result))
    (catch Exception e
      (println "❌ Error:" (.getMessage e)))))

(defn test-show-specific-block []
  (println "🔍 Showing block 1jZf_BCA7 with all layers")
  (try
    (let [block-uid "1jZf_BCA7"
          result (client/roam-pull-deep :lisp block-uid)]
      (println "📋 Block 1jZf_BCA7 structure:")
      (println result))
    (catch Exception e
      (println "❌ Error:" (.getMessage e)))))
