(ns qq.tui
  "Minimal reactive TUI framework with filtering"
  (:require [clojure.string :as str]
            [babashka.process :as process]))

(def ^:private last-rendered-content (atom nil))

(defn clear-screen []
  "\033[2J\033[H\033[0m")

(defn move-cursor [row col]
  (str "\033[" row ";" col "H"))

(defn render-frame [content]
  (let [current-content @last-rendered-content]
    (when-not (= content current-content)
      (print (clear-screen))
      (print content)
      (flush)
      (reset! last-rendered-content content))))

(defn setup-raw-mode []
  (try
    (let [proc (process/process ["stty" "raw" "-echo"] {:inherit true})]
      (-> proc deref :exit zero?))
    (catch Exception e false)))

(defn restore-terminal []
  (try
    (let [proc (process/process ["stty" "sane"] {:inherit true})]
      (-> proc deref :exit zero?))
    (catch Exception e false)))

(defn fuzzy-match [query text]
  "Simple fuzzy matching - checks if all query chars appear in order in text"
  (if (str/blank? query)
    true
    (let [query-chars (str/lower-case query)
          text-chars (str/lower-case text)]
      (loop [q-idx 0 t-idx 0]
        (cond
          (>= q-idx (count query-chars)) true
          (>= t-idx (count text-chars)) false
          (= (nth query-chars q-idx) (nth text-chars t-idx))
          (recur (inc q-idx) (inc t-idx))
          :else (recur q-idx (inc t-idx)))))))

(defn filter-items [query items]
  "Filter items based on fuzzy matching"
  (if (str/blank? query)
    items
    (filter #(fuzzy-match query (str %)) items)))

(defn check-key-press []
  (try
    (let [available (.available System/in)]
      (when (> available 0)
        (let [key-code (.read System/in)]
          (cond
            (= key-code 27) (let [next1 (.read System/in)]
                             (if (= next1 91) ; [
                               (case (.read System/in)
                                 65 :up    ; A
                                 66 :down  ; B
                                 67 :right ; C  
                                 68 :left  ; D
                                 :unknown)
                               :escape))
            (= key-code 10) :enter  ; Enter
            (= key-code 13) :enter  ; CR
            (= key-code 3) :ctrl-c  ; Ctrl+C
            (= key-code 14) :ctrl-n  ; Ctrl+N (down)
            (= key-code 16) :ctrl-p  ; Ctrl+P (up)
            (= key-code 11) :ctrl-k  ; Ctrl+K (up)
            (= key-code 32) :space
            (= key-code 127) :backspace  ; DEL
            (= key-code 8) :backspace    ; BS
            (= key-code 47) :slash       ; /
            (and (>= key-code 32) (<= key-code 126)) (char key-code) ; Printable chars
            :else nil))))
    (catch Exception e nil)))

(defn read-char []
  "Read a single character for text input"
  (try
    (let [available (.available System/in)]
      (when (> available 0)
        (let [key-code (.read System/in)]
          (when (and (>= key-code 32) (<= key-code 126))
            (char key-code)))))
    (catch Exception e nil)))

(defn create-app [initial-state render-fn]
  {:state (atom initial-state)
   :render render-fn
   :running (atom true)})

(defn create-filter-selector
  "High-level API for creating a filterable selector with automatic management"
  [items render-fn & {:keys [refresh-fn refresh-interval cursor-blink]
                      :or {refresh-interval 2000 cursor-blink true}}]
  (let [app (create-app 
              {:items items :selected 0 :query "" :cursor-visible true}
              render-fn)]
    
    ; Auto-manage cursor blinking if enabled
    (when cursor-blink
      (future
        (loop []
          (Thread/sleep 500)
          (when @(:running app)
            (swap! (:state app) update :cursor-visible not)
            (recur)))))
    
    ; Auto-manage background refresh if provided
    (when refresh-fn
      (future
        (loop []
          (Thread/sleep refresh-interval)
          (when @(:running app)
            (let [new-items (refresh-fn)]
              (when (not= new-items (:items @(:state app)))
                (swap! (:state app) assoc :items new-items)))
            (recur)))))
    
    app))

(defn start-filter-app [app]
  "Enhanced app with filtering support"
  (let [{:keys [state render running]} app]
    (setup-raw-mode)
    
    ; Setup reactive rendering
    (add-watch state :render
      (fn [_ _ _ new-state]
        (when @running
          (render-frame (render new-state)))))
    
    ; Initial render
    (render-frame (render @state))
    
    ; Event loop with filtering support
    (try
      (loop []
        (when @running
          (let [key (check-key-press)]
            (cond
              (= key :ctrl-c) (reset! running false)
              (= key :escape) (swap! state assoc :query "")
              (= key :backspace) (swap! state update :query #(if (empty? %) "" (subs % 0 (dec (count %)))))
              (#{:up :ctrl-p :ctrl-k} key) (let [filtered-count (count (filter-items (:query @state) (:items @state)))]
                    (swap! state update :selected #(max 0 (dec %))))
              (#{:down :ctrl-n} key) (let [filtered-count (count (filter-items (:query @state) (:items @state)))]
                      (swap! state update :selected #(min (dec filtered-count) (inc %))))
              (= key :enter) (do (reset! running false)
                        (let [filtered-items (filter-items (:query @state) (:items @state))
                              selected-idx (:selected @state)]
                          (when (< selected-idx (count filtered-items))
                            (swap! state assoc :result (nth filtered-items selected-idx)))))
              (char? key) (do
                           (swap! state update :query str key)
                           (swap! state assoc :selected 0)) ; Reset selection when typing
              :else nil))
          (when @running
            (Thread/sleep 50)
            (recur))))
      (catch Exception e
        (reset! running false))
      (finally
        (restore-terminal)))
    
    (:result @state)))

(defn default-item-display [item]
  "Default way to display an item"
  (cond
    (map? item) (or (:name item) (:title item) (str item))
    (string? item) item
    :else (str item)))

(defn default-render [state & {:keys [title item-fn] 
                               :or {title "SELECT ITEM" item-fn default-item-display}}]
  "Default gum-style renderer"
  (let [{:keys [items selected query cursor-visible]} state
        filtered-items (filter-items (or query "") items)
        header (str "🎯 " title "\r\n" (apply str (repeat (+ 4 (count title)) "=")) "\r\n")
        cursor (if cursor-visible "█" " ")
        search-line (str "🔍 Filter: " (or query "") cursor "\r\n")
        item-list (clojure.string/join "\r\n" 
                    (map-indexed 
                      (fn [idx item]
                        (let [prefix (if (= idx selected) "► " "  ")
                              display (item-fn item)]
                          (str prefix display)))
                      filtered-items))
        footer "\r\n\r\n↑↓/Ctrl+P/Ctrl+N/Ctrl+K Navigate | Esc Clear | Enter Select | Backspace Delete | Ctrl+C Quit"]
    (str header search-line item-list footer)))

(defn select-from
  "Gum-style API: auto-renders by default, customizable for picky users"
  [data-or-fn & {:keys [render-fn title item-fn refresh-interval] 
                 :or {refresh-interval 2000}}]
  (let [is-function? (fn? data-or-fn)
        initial-items (if is-function? (data-or-fn) data-or-fn)
        refresh-fn (when is-function? data-or-fn)
        ; Use custom render-fn OR default with customizations
        final-render-fn (or render-fn 
                           #(default-render % :title title :item-fn item-fn))
        app (create-filter-selector 
              initial-items 
              final-render-fn
              :refresh-fn refresh-fn
              :refresh-interval refresh-interval)]
    (start-filter-app app)))

(defn start-app [app]
  "Original simple app without filtering"
  (let [{:keys [state render running]} app]
    (setup-raw-mode)
    
    (add-watch state :render
      (fn [_ _ _ new-state]
        (when @running
          (render-frame (render new-state)))))
    
    (render-frame (render @state))
    
    (try
      (loop []
        (when @running
          (let [key (check-key-press)]
            (case key
              (:q :ctrl-c) (reset! running false)
              :up (swap! state update :selected #(max 0 (dec %)))
              :down (swap! state update :selected #(min (dec (count (:items @state))) (inc %)))
              :enter (do (reset! running false)
                        (swap! state assoc :result (nth (:items @state) (:selected @state))))
              nil))
          (when @running
            (Thread/sleep 50)
            (recur))))
      (catch Exception e
        (reset! running false))
      (finally
        (restore-terminal)))
    
    (:result @state)))
