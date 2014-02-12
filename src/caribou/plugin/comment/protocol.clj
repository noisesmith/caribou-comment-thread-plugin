(ns caribou.plugin.comment.protocol)

(defrecord DefaultComment [kslug validate config])

(defn kslug
  [this]
  :comment)

(defn validate-impl
  [this request]
  (constantly true))

(defn create-comment
  [this {:keys [parent-id thread-id]}]
  (model/create (:kslug this) {:parent-id parent-id :thread-id thread-id}))

(def selection-map
  [id depth]
  (let [includes (last
                  (take (inc depth)
                        (iterate (fn [m] (assoc {} :children m)) {})))]
    {:where {:id id} :include includes}))

(defn retrieve-thread
  [this {id :id depth :depth :as opts
         :or {depth 20}}]
  (let []
    (model/pick (:kslug this) (selection-map id depth))))

