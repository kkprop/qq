(ns qq.tui
  "Minimal reactive TUI framework"
  (:require [clojure.string :as str]
            [babashka.process :as process]))

(ns qq.tui
  "Minimal reactive TUI framework"
  (:require [clojure.string :as str]
            [babashka.process :as process]))

(def ^:private last-rendered-content (atom nil))

(defn clear-screen []
  "\033[2J\033[H\033[0m")  ; Clear screen, home cursor, reset attributes

(defn move-cursor [row col]
  (str "\033[" row ";" col "H"))

(defn render-frame [content]
  (let [current-content @last-rendered-content]
    (if (= content current-content)
      ; No change, don't render
      nil
      ; Content changed, use differential rendering
      (let [lines (str/split-lines content)
            current-lines (if current-content 
                           (str/split-lines current-content) 
                           [])]
        ; For now, use simple approach: only clear and redraw if content changed
        ; Future optimization: implement line-by-line differential updates
        (print (clear-screen))
        (print content)
        (flush)
        (reset! last-rendered-content content)))))

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

(defn check-key-press []
  (try
    (let [available (.available System/in)]
      (when (> available 0)
        (let [key-code (.read System/in)]
          (case key-code
            27 (let [next1 (.read System/in)]
                 (if (= next1 91) ; [
                   (case (.read System/in)
                     65 :up    ; A
                     66 :down  ; B
                     67 :right ; C  
                     68 :left  ; D
                     :unknown)
                   :escape))
            10 :enter
            13 :enter  ; CR
            113 :q
            3 :ctrl-c  ; Ctrl+C
            32 :space
            :unknown))))
    (catch Exception e nil)))

(defn create-app [initial-state render-fn]
  {:state (atom initial-state)
   :render render-fn
   :running (atom true)})

(defn start-app [app]
  (let [{:keys [state render running]} app]
    (setup-raw-mode)
    
    ; Setup reactive rendering
    (add-watch state :render
      (fn [_ _ _ new-state]
        (when @running
          (render-frame (render new-state)))))
    
    ; Initial render
    (render-frame (render @state))
    
    ; Event loop
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
            (Thread/sleep 50) ; Check every 50ms
            (recur))))
      (catch Exception e
        (reset! running false))
      (finally
        (restore-terminal)))
    
    ; Return result
    (:result @state)))
