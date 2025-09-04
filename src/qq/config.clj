(ns qq.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]))

(def config-path "config.edn")

(defn load-config []
  (if (.exists (io/file config-path))
    (edn/read-string (slurp config-path))
    {}))

(defn get-config [key & [default]]
  (get (load-config) key default))

(defn save-config [config]
  (with-open [w (io/writer config-path)]
    (pprint/pprint config w)))

(defn setup-config []
  (println "🔧 Setting up QQ configuration...")
  
  (if (.exists (io/file config-path))
    (println "✅ Config file already exists")
    (do
      (save-config {:roam-graphs {:personal {:token "your-personal-token"
                                            :graph "personal-graph"}
                                  :work {:token "your-work-token"
                                         :graph "work-graph"}}
                    :default-graph :personal})
      
      (println "✅ Created config.edn")
      (println "🔑 Edit config.edn and add your Roam Research credentials")
      (println "💡 You can have multiple graphs - just add more entries under :roam-graphs"))))
