(ns qq.roam.blocks
  (:require [clojure.string :as str]
            [qq.roam.client :as client]))

(defn extract-entities [text]
  "Extract [[entity]] patterns from text"
  (map second (re-seq #"\[\[([^\]]+)\]\]" text)))

(defn find-latest-block-uid [graph-key search-text]
  "Find the UID of the most recent block containing search text"
  (let [query "[:find ?uid :in $ ?search-text :where [?b :block/uid ?uid] [?b :block/string ?s] [(clojure.string/includes? ?s ?search-text)]]"
        result (client/roam-query graph-key query [search-text])]
    (when (:result result)
      (-> result :result last first))))

(defn create-qa-block [graph-key question answer entities]
  "Create layered Q&A with entity links: Question -> Answer -> Entities"
  (let [question-text (str "Q: " question)
        answer-text (str "A: " answer)
        entities-text (when (seq entities)
                        (str "Related: " (str/join ", " entities)))]
    
    ;; Step 1: Create question block
    (let [q-result (client/roam-write graph-key question-text)]
      (if (:success q-result)
        (do
          ;; Step 2: Find the question block UID
          (Thread/sleep 500)
          (let [question-uid (find-latest-block-uid graph-key question-text)]
            (if question-uid
              ;; Step 3: Create answer as child of question
              (let [a-result (client/roam-write-to-parent graph-key question-uid answer-text)]
                (if (and (:success a-result) entities-text)
                  ;; Step 4: Add entities as child of answer
                  (let [answer-uid (find-latest-block-uid graph-key answer-text)
                        e-result (when answer-uid
                                   (client/roam-write-to-parent graph-key answer-uid entities-text))]
                    {:success true 
                     :question-result q-result 
                     :answer-result a-result
                     :entities-result e-result
                     :question-uid question-uid})
                  {:success true 
                   :question-result q-result 
                   :answer-result a-result
                   :question-uid question-uid}))
              {:success false :error "Could not find question block UID"})))
        q-result))))
