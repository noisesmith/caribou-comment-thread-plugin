(ns caribou.plugin.comment
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire]
            [caribou.plugin.protocol :as plugin]
            [caribou.model :as model]))

(defrecord CommentPlugin [create-comment retrieve-thread config])

(defn migrate
  [this config]
  (model/with-models config
    (model/create
     :model
     {:name "Comment"
      :fields [{:name "Thread Id" :type "string"}
               {:name "Author Name" :type "string"}
               {:name "Body" :type "text"}]})
    (model/update
     :model (model/models :comment :id)
     {:fields [{:name "Children"
                :type "collection"
                :reciprocal-name "Parent"
                :target-id (model/models :comment :id)}]})))

(defn rollback
  [this config]
  (model/with-models config
    (model/destroy :model (model/models :comment :id))))

(defn read-int
  [s]
  ((fnil #(Integer/parseInt %) "0")
   (when (string? s) (re-find #"\d+" s))))

(defn create-comment
  [name parent-id body]
  (model/create :comment
                {:author-name name
                 :parent-id (read-int parent-id)
                 :body body}))

(defn n-children
  [depth]
  (reduce (fn [m _] {:children m})
          {}
          (range depth)))

(defn retrieve-thread
  [id]
  (model/pick :comment
              {:where {:id (read-int id)}
               :include (n-children 10)}))

;; the below controllers rely on this being a singleton class,
;; this is neccessary because caribou needs a top level var for
;; each page controller

(defonce instance (atom nil))

(defn ajax-json-get
  [request]
  (let [lookup (:retrieve-thread @instance)
        id (-> request :params :comment-thread-id)
        content (lookup id)
        body (try (cheshire/generate-string (lookup id))
                  (catch Throwable t :failed))]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body body}))

(defonce debug (atom nil))

(defn ajax-json-put
  [request]
  (reset! debug request)
  (let [params (:params request)
        {parent-id :parent-id
         session :session
         body :body} params
        comment ((:create-comment @instance) (:name session) parent-id body)]
    (if true ; (validate this parent-id thread-id session)
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (cheshire/generate-string comment)})))

(defn comment-helper
  [this render]
  (fn [id]
    (render ((:retrieve-thread this) id))))

(def default-impl
  {:create-comment create-comment
   :retrieve-thread retrieve-thread})

(defn create
  [implementation config]
  (let [plugin (map->CommentPlugin
                (merge default-impl implementation {:config config}))]
    (reset! instance plugin)
    plugin))

(plugin/make
   CommentPlugin
   {:update-config (fn [this config]
                     (assoc-in config [:comments :render]
                               cheshire/generate-string))
    :apply-config (fn [this config] (create this config))
    :migrate (fn [this config] {:name "comment-thread"
                                :migration migrate
                                :rollback rollback})
    :provide-helpers (fn [this config]
                       {:comment (comment-helper
                                  this
                                  (-> config :comments :render))})
    :provide-handlers (fn [this config] {})
    :provide-pages (fn [this config]
                     {:comments [{:path "get-comments/:comment-thread-id"
                                  :name "Get Comments"
                                  :slug "get-comments"
                                  :template ""
                                  :controller #'ajax-json-get}
                                 {:path "submit-comment"
                                  :name "Submit Comment"
                                  :slug "submit-comment"
                                  :template ""
                                  :controller #'ajax-json-put
                                  :method :POST}]})})
