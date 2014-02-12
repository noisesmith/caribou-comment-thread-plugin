(ns caribou.plugin.comment
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire]
            [caribou.plugin.protocol :as plugin]
            [caribou.plugin.comment.protocol :as commentable]
            [caribou.model :as model]))

(defrecord CommentPlugin [create-comment retrieve-thread])

(defn create
  [this implementation config]
  (map->CommentPlugin
   (merge default/impl implementation {:config config})))

(defn migrate
  [this config]
  (model/with-models config
    (model/create
     :model
     {:name (string/capitalize (name (kslug this)))
      :fields [{:name "Thread Id" :type "string"}
               {:name "Author Name" :type "string"}
               {:name "Body" :type "text"}]})
    (model/update
     :model (model/models (kslug this) :id)
     {:fields [{:name "Children"
                :type "collection"
                :reciprocal-name "Parent"
                :target-id (model/models (kslug this) :id)}]})))

(defn rollback
  [this config]
  (model/with-models config
    (model/destroy :model (model/models (kslug this) :id))))

(defn ajax-json-get
  [this]
  (fn [request]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/generate-string
            (retrieve-thread this (retrieve-thread this (:params request))))}))

(defn ajax-json-put
  [this]
  (fn [request]
    (let [params {:params request}
          {parent-id :parent-id
           session :session
           body :body} params]
      (if (validate this parent-id thread-id session)
        (do (model/create (kslug this)
                          {:author-name (:name session)
                           :parent-id (retrieve-thread this params)
                           :body }))))))

(defn comment-helper
  [id & [render]])

(extend commentable.DefaultComment
  CommentPlugin
  {:create-comment commentable/create-comment
   :retrieve-thread commentable/retrieve-thread})

(plugin/make
 CommentPlugin
 {:update-config (fn [this config] config)
  :apply-config (fn [this config] (create this config))
  :migrate (fn [this config] {:name "comment-thread"
                              :migration migrate
                              :rollback rollback})
  :provide-helpers (fn [this] {})
  :provide-handlers (fn [this] {})
  :provide-pages (fn [this config] {})})
