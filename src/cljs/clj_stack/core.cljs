(ns clj-stack.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [cljs.core.async :refer [chan put! <!]]
            [om-sync.util :refer [edn-xhr]]))

(enable-console-print!)

(def app-model (atom {:videos []}))


(defn add-video [videos owner]
  (let [title (-> (om/get-node owner "new-video-title") .-value)
        url (-> (om/get-node owner "new-video-url") .-value)
        video-data {:video/title title :video/url url}]
    (om/transact! videos #(conj % video-data))
    (edn-xhr {:url "/videos" :method :post :data video-data})
    (om/set-state! owner :new-video-name "")
    (om/set-state! owner :new-video-url "")))

(defn delete-video [videos del-video]
  (om/transact! videos
    (fn [vs] (vec (remove #(= % del-video) vs))))
  (edn-xhr {:url (str "/videos/" (:db/id del-video)) :method :delete}))

(defn new-video-view [videos owner]
  (reify
    om/IInitState
    (init-state [_] {:new-video-name "" :new-video-url ""})
    om/IRenderState
    (render-state [_ state]
            (dom/div nil
             (dom/input #js {:ref "new-video-title" :type "text" :placeholder "Title"
                             :value (:new-video-name state)
                             :onChange #(om/set-state! owner :new-video-name (.. % -target -value))})
             (dom/input #js {:ref "new-video-url" :type "text" :placeholder "URL"
                             :value (:new-video-url state)
                             :onChange #(om/set-state! owner :new-video-url (.. % -target -value))})
             (dom/button #js {:onClick #(add-video videos owner)} "Add")))))

(defn video-view [video owner]
  (reify om/IRenderState
    (render-state [_ {:keys [delete_channel]}]
      (dom/li #js {:className "col-sm-4"}
        (dom/div #js {:className "embed-responsive embed-responsive-4by3"}
          (dom/iframe #js {:className "embed-responsive-item"
                           :src (:video/url video)}))
        (dom/button #js {:className "btn btn-danger btn-xs pull-right"
                       :onClick #(put! delete_channel video)} "Remove" )))))


(defn videos-view [videos owner]
  (reify
    om/IInitState
    (init-state [_] {:delete_channel (chan)})
    om/IWillMount
    (will-mount [_]
      (let [delete_channel (om/get-state owner :delete_channel)]
        (go (loop []
          (let [del-video (<! delete_channel)]
            (delete-video videos del-video)
            (recur))))))
    om/IRenderState
    (render-state [_ {:keys [delete_channel]}]
      (apply dom/ul #js {:className "row"}
        (om/build-all video-view videos
                      {:init-state {:delete_channel delete_channel}})))))


(defn app-view [model owner]
  (reify om/IRender
      (render [_]
        (dom/div nil
          (dom/div #js {:className "page-header"}
            (dom/h2 nil "Poor man's YouTube"))
          (om/build videos-view (:videos model))
          (dom/hr nil)
          (om/build new-video-view (:videos model))))))

(edn-xhr
 (let [target (. js/document (getElementById "app"))]
   {:method :get
    :url "/videos"
    :on-complete (fn [res]
                   (reset! app-model res)
                   (om/root app-view res {:target target}))}))

