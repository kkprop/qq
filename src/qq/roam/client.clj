(ns qq.roam.client
  (:require [clojure.data.json :as json]
            [babashka.http-client :as http]
            [qq.config :as config]))

(def base-url "https://api.roamresearch.com/api/graph")

(defn get-graph-config [graph-key]
  (let [graphs (config/get-config :roam-graphs)
        default-graph (config/get-config :default-graph)]
    (get graphs (or graph-key default-graph))))

(defn cur-daily-page []
  "Get today's daily note UID in MM-dd-yyyy format"
  (let [formatter (java.text.SimpleDateFormat. "MM-dd-yyyy")]
    (.format formatter (java.util.Date.))))

(defn roam-request [method endpoint graph-key data]
  (let [{:keys [token graph]} (get-graph-config graph-key)
        url (str base-url "/" graph "/" endpoint)]
    (http/request
     {:method method
      :url url
      :headers {"Authorization" (str "Bearer " token)
                "Content-Type" "application/json"
                "Accept" "application/json"}
      :body (json/write-str data)})))

(defn roam-write-to-parent [graph-key parent-uid string-content]
  "Write a block as child of specific parent"
  (let [data {:action "create-block"
              :location {:parent-uid parent-uid
                         :order "last"}
              :block {:string string-content
                      :open false}}
        response (roam-request :post "write" graph-key data)]
    (if (= 200 (:status response))
      {:success true :message "Block created successfully" :parent-uid parent-uid}
      {:success false :error "Request failed" :status (:status response) :body (:body response)})))

(defn roam-write [graph-key string-content]
  "Simple write to today's daily note"
  (roam-write-to-parent graph-key (cur-daily-page) string-content))

(defn roam-pull [graph-key block-uid]
  "Pull a block and its children by UID"
  (let [data {:eid (str "[:block/uid \"" block-uid "\"]")
              :selector "[:block/uid :block/string {:block/children [:block/uid :block/string]}]"}
        response (roam-request :post "pull" graph-key data)]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      {:error "Request failed" :status (:status response)})))

(defn roam-query [graph-key query args]
  "Query blocks using Datalog to find block UIDs"
  (let [data {:query query :args args}
        response (roam-request :post "q" graph-key data)]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      {:error "Request failed" :status (:status response)})))

(defn roam-pull-page-blocks [graph-key page-title]
  "Pull all blocks and their nested children from a specific page"
  (let [data {:eid (str "[:node/title \"" page-title "\"]")
              :selector "[:node/title :block/uid {:block/children ...}]"}
        response (roam-request :post "pull" graph-key data)]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      {:error "Request failed" :status (:status response)})))

(defn roam-pull-deep [graph-key block-uid]
  "Pull a block with all nested children recursively"
  (let [data {:eid (str "[:block/uid \"" block-uid "\"]")
              :selector "[:block/uid :block/string {:block/children ...}]"}
        response (roam-request :post "pull" graph-key data)]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      {:error "Request failed" :status (:status response)})))

(defn format-block-tree [block indent]
  "Format block tree with readable indentation"
  (let [string-key (keyword ":block" "string")
        children-key (keyword ":block" "children")
        content (get block string-key (get block :string ""))
        children (get block children-key (get block :children []))]
    (str (apply str (repeat indent "  ")) "- " content "\n"
         (apply str (map #(format-block-tree % (inc indent)) children)))))

(defn show-page-layers [graph-key page-title]
  "Show all layered blocks from page content AND blocks that reference the page"
  (println (str "📖 Showing layers for page: " page-title))
  
  ;; Show content ON the page
  (println "\n📄 Content ON the page:")
  (let [page-result (roam-pull-page-blocks graph-key page-title)]
    (if (:error page-result)
      (println "❌ Error reading page:" (:error page-result))
      (let [page-block (:result page-result)
            children-key (keyword ":block" "children")
            uid-key (keyword ":block" "uid")
            children (get page-block children-key [])]
        (if (empty? children)
          (println "📋 No content on this page")
          (doseq [child children]
            (let [child-uid (get child uid-key (:uid child))
                  deep-child (roam-pull-deep graph-key child-uid)]
              (when-not (:error deep-child)
                (print (format-block-tree (:result deep-child) 0)))))))))
  
  ;; Show blocks that REFERENCE the page
  (println "\n🔗 Blocks that REFERENCE this page:")
  (let [query "[:find ?uid :in $ ?page-title :where [?b :block/uid ?uid] [?b :block/refs ?page] [?page :node/title ?page-title]]"
        refs-result (roam-query graph-key query [page-title])]
    (if (:error refs-result)
      (println "❌ Error finding references:" (:error refs-result))
      (let [block-uids (map first (:result refs-result))]
        (if (empty? block-uids)
          (println "📋 No blocks reference this page")
          (doseq [uid block-uids]
            (let [block-result (roam-pull-deep graph-key uid)]
              (when-not (:error block-result)
                (print (format-block-tree (:result block-result) 0))))))))))
