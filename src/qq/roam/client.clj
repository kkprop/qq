(ns qq.roam.client
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.http-client :as http]
            [qq.config :as config]
            [qq.markdown :as md]
            [qq.tui :as tui]))

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

(defn write-block-recursively [graph-key block]
  "Write a block and its children with proper indentation"
  (roam-write graph-key (:text block))
  (when (:children block)
    (doseq [child (:children block)]
      (roam-write graph-key (str "  " (:text child)))
      (when (:children child)
        (doseq [grandchild (:children child)]
          (roam-write graph-key (str "    " (:text grandchild))))))))

(defn find-blocks-created-after [graph-key timestamp]
  "Find blocks created after specific timestamp"
  (let [query "[:find ?uid ?string :in $ ?timestamp :where [?b :block/uid ?uid] [?b :block/string ?string] [?b :create/time ?time] [(> ?time ?timestamp)]]"
        result (roam-query graph-key query [timestamp])]
    (if (:error result)
      {:error (:error result)}
      {:blocks (map (fn [[uid string]] {:uid uid :string string}) (:result result))})))

(defn write-with-uid-capture [graph-key content]
  "Write content and capture the UID by timestamp"
  (let [before-timestamp (System/currentTimeMillis)
        result (roam-write graph-key content)]
    (if (:success result)
      (do
        ;; Wait a moment for the block to be indexed
        (Thread/sleep 1000)
        ;; Find blocks created after our timestamp
        (let [new-blocks (find-blocks-created-after graph-key before-timestamp)]
          (if (:error new-blocks)
            result
            ;; Return result with captured UID
            (assoc result :uid (-> new-blocks :blocks first :uid)))))
      result)))

(defn write-with-retry [graph-key content max-retries delay-ms]
  "Write with retry strategy for rate limits"
  (loop [attempt 1]
    (let [result (roam-write graph-key content)]
      (if (or (:success result) (>= attempt max-retries))
        result
        (do
          (println "⏳ Rate limited, waiting" delay-ms "ms... (attempt" attempt "/" max-retries ")")
          (Thread/sleep delay-ms)
          (recur (inc attempt)))))))

(defn chunk-by-h2-headers [markdown-text]
  "Split markdown into chunks by H2 headers, keeping sub-content together"
  (let [lines (str/split-lines markdown-text)
        chunks (atom [])
        current-chunk (atom [])]
    (doseq [line lines]
      (if (str/starts-with? line "## ")
        ;; New H2 header - start new chunk
        (do
          (when (seq @current-chunk)
            (swap! chunks conj (str/join "\n" @current-chunk)))
          (reset! current-chunk [line]))
        ;; Add to current chunk
        (swap! current-chunk conj line)))
    ;; Add final chunk
    (when (seq @current-chunk)
      (swap! chunks conj (str/join "\n" @current-chunk)))
    @chunks))

(defn parse-hierarchical-chunks [markdown-text]
  "Parse markdown into H1 root with H2 children containing H3+ content"
  (let [lines (str/split-lines markdown-text)
        h1-title (atom nil)
        h2-chunks (atom [])
        current-h2 (atom nil)
        current-content (atom [])]
    
    (doseq [line lines]
      (cond
        ;; H1 header - capture as root title
        (str/starts-with? line "# ")
        (reset! h1-title line)
        
        ;; H2 header - start new child block
        (str/starts-with? line "## ")
        (do
          ;; Save previous H2 block if exists
          (when @current-h2
            (swap! h2-chunks conj {:h2 @current-h2 :content (str/join "\n" @current-content)}))
          ;; Start new H2
          (reset! current-h2 line)
          (reset! current-content []))
        
        ;; H3+ headers or content - add to current H2
        :else
        (when @current-h2  ; Only add if we have an H2 context
          (swap! current-content conj line))))
    
    ;; Add final H2 chunk
    (when @current-h2
      (swap! h2-chunks conj {:h2 @current-h2 :content (str/join "\n" @current-content)}))
    
    {:h1 @h1-title :h2-blocks @h2-chunks}))

(defn find-block-by-content [graph-key content]
  "Find block UID by matching exact content"
  (let [query "[:find ?uid :in $ ?content :where [?b :block/uid ?uid] [?b :block/string ?content]]"
        result (roam-query graph-key query [content])]
    (if (:error result)
      nil
      (-> result :result first first))))  ; Get first UID from results

(defn write-hierarchical-blocks [graph-key markdown-text]
  "Write H1 as root parent, H2 as children, H3+ content as grandchildren"
  (let [parsed (parse-hierarchical-chunks markdown-text)
        h1-title (:h1 parsed)
        h2-blocks (:h2-blocks parsed)
        h2-count (count h2-blocks)]
    
    (println "📝 Writing H1 root with" h2-count "H2 children to" (name graph-key))
    
    ;; Write H1 as root parent and capture UID
    (println "📋 Writing H1 root parent...")
    (let [h1-result (write-with-uid-capture graph-key h1-title)]
      (if (:success h1-result)
        (do
          (println "✅ H1 root posted, UID:" (:uid h1-result))
          
          ;; Write each H2 as child of H1
          (doseq [[i h2-block] (map-indexed vector h2-blocks)]
            (println "📋 Writing H2 child" (inc i) "/" h2-count "...")
            
            ;; Write H2 as child of H1
            (let [h2-result (roam-write-to-parent graph-key (:uid h1-result) (:h2 h2-block))]
              (if (:success h2-result)
                (do
                  (println "✅ H2 child posted")
                  
                  ;; Find the H2 UID by matching its exact content
                  (when (not (str/blank? (:content h2-block)))
                    (println "📋 Finding H2 UID by content match...")
                    (Thread/sleep 1000) ; Wait for indexing
                    (let [h2-uid (find-block-by-content graph-key (:h2 h2-block))]
                      (if h2-uid
                        (let [content-result (roam-write-to-parent graph-key h2-uid (:content h2-block))]
                          (if (:success content-result)
                            (println "✅ H3+ content posted under H2, UID:" h2-uid)
                            (println "❌ H3+ content failed")))
                        (println "❌ Could not find H2 UID by content")))))
                (println "❌ H2 child failed")))))
        (println "❌ H1 root failed")))))

(defn add-draft-to-bin [response-text graph-key]
  "Add draft response to bin file for specific graph - auto-detects file paths"
  (let [content (if (and (string? response-text)
                        (.exists (io/file response-text)))
                 ;; It's a file path - read the content
                 (do
                   (println "📖 Reading content from file:" response-text)
                   (slurp response-text))
                 ;; It's direct text content
                 response-text)
        draft-file (str "drafts/" (name graph-key) "-drafts.edn")
        timestamp (str (java.time.Instant/now))
        draft {:text content :timestamp timestamp :graph graph-key :source (if (.exists (io/file response-text)) response-text "direct")}]
    (io/make-parents draft-file)
    (let [existing-drafts (if (.exists (io/file draft-file))
                           (read-string (slurp draft-file))
                           [])]
      (spit draft-file (pr-str (conj existing-drafts draft)))
      (println "📝 Draft added to" (name graph-key) "bin (" (inc (count existing-drafts)) " total)"))))

(defn write-response-to-monkeyq [graph-key response-text]
  "Write our response under #MonkeyQ #DcQ tags"
  (let [timestamp (java.time.Instant/now)
        formatted-response (str "#MonkeyQ #DcQ " timestamp "\n" response-text)]
    (roam-write graph-key formatted-response)))

(defn post-all-drafts [graph-key]
  "Review and post selected drafts with TUI selection and Accept/Reject/Keep workflow"
  (let [draft-file (str "drafts/" (name graph-key) "-drafts.edn")]
    (if (.exists (io/file draft-file))
      (let [drafts (read-string (slurp draft-file))
            draft-count (count drafts)]
        (if (empty? drafts)
          (println "📭 No drafts in" (name graph-key) "bin")
          (do
            (println "📋" draft-count "drafts available in" (name graph-key) "bin")
            
            ;; Use TUI to select which draft to review
            (let [draft-options (map-indexed 
                                (fn [i draft] 
                                  {:index i 
                                   :draft draft
                                   :preview (let [text (:text draft)]
                                            (if (> (count text) 120)
                                              (str (subs text 0 120) "...")
                                              text))})
                                drafts)
                  selected (tui/select-from 
                           draft-options
                           :title "Select draft to review:"
                           :item-fn (fn [option] 
                                     (str (inc (:index option)) ". " (:preview option))))]
              
              (if selected
                (let [draft-to-review (:draft selected)]
                  ;; Show full content with proper formatting
                  (println "\n📄 Full draft content:")
                  (println (str/join "" (repeat 60 "=")))
                  ;; Replace \n with actual line breaks for readability
                  (println (str/replace (:text draft-to-review) "\\n" "\n"))
                  (println (str/join "" (repeat 60 "=")))
                  
                  ;; Use TUI confirm for each action
                  (cond
                    (tui/confirm "Accept and post to Roam?")
                    (do
                      (println "📋 Posting to" (name graph-key) "...")
                      (write-hierarchical-blocks graph-key (:text draft-to-review))
                      (println "✅ Posted to" (name graph-key) "graph")
                      
                      ;; Remove from drafts
                      (let [remaining-drafts (vec (remove #(= % draft-to-review) drafts))]
                        (spit draft-file (pr-str remaining-drafts)))
                      
                      (when (= graph-key :yuanvv)
                        (roam-write graph-key (str "#DcQ #Posted \"Posted 1 response at " (java.time.Instant/now) "\""))))
                    
                    (tui/confirm "Reject and remove from drafts?" false)
                    (do
                      (println "❌ Draft rejected and removed")
                      ;; Remove from drafts
                      (let [remaining-drafts (vec (remove #(= % draft-to-review) drafts))]
                        (spit draft-file (pr-str remaining-drafts))))
                    
                    :else
                    (println "📝 Draft kept for later review")))
                (println "❌ No draft selected"))))))
      (println "📭 No drafts in" (name graph-key) "bin"))))
