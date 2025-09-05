(ns qq.markdown
  (:require [clojure.string :as str]))

(defn parse-markdown-to-blocks [markdown-text]
  "Parse markdown text into hierarchical block structure"
  (let [lines (str/split-lines markdown-text)
        blocks (atom [])]
    (doseq [line lines]
      (cond
        ;; H1-H6 headers (# ## ### #### ##### ######)
        (re-matches #"^#{1,6} .*" line)
        (let [header-level (count (take-while #(= % \#) line))
              text (str/trim (subs line header-level))]
          (swap! blocks conj {:level header-level :text text :children []}))
        
        ;; Indented bullet points (  - ,    - , etc.)
        (re-matches #"^(\s*)- .*" line)
        (let [indent-level (count (re-find #"^\s*" line))
              bullet-level (+ 7 (quot indent-level 2)) ; Start bullets at level 7+
              text (str/trim (subs line (+ indent-level 2)))]
          (swap! blocks conj {:level bullet-level :text text :children []}))
        
        ;; Regular bullet points (- )
        (str/starts-with? line "- ")
        (swap! blocks conj {:level 7 :text (subs line 2) :children []})
        
        ;; Regular text (non-empty)
        (and (not (str/blank? line)) (not (str/starts-with? line "#")))
        (swap! blocks conj {:level 10 :text line :children []})))
    @blocks))

(defn build-hierarchy [flat-blocks]
  "Convert flat blocks into nested hierarchy - simplified version"
  ;; For now, just return flat blocks as top-level items
  ;; TODO: Implement proper nesting later
  (map (fn [block] 
         (assoc block :children [])) 
       flat-blocks))

(defn blocks-to-roam-structure [blocks]
  "Convert hierarchical blocks to Roam write format"
  (map (fn [block]
         {:text (:text block)
          :children (when (seq (:children block))
                     (blocks-to-roam-structure (:children block)))})
       blocks))

(defn markdown-to-roam-blocks [markdown-text]
  "Complete pipeline: markdown → hierarchical Roam blocks"
  (-> markdown-text
      parse-markdown-to-blocks
      build-hierarchy
      blocks-to-roam-structure))
