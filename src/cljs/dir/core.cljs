(ns dir.core
    (:require [reagent.core :as reagent :refer [atom]]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [dir.air :as air]
              [dir.state :refer [state]]
              [clojure.string :as str]))

;; -------------------------
;; Styles

(def flex-v {:display "flex" :flex-direction "column" :padding 5})

(def child {:margin 5})

(def small {:font-size "0.7em"})

;; -------------------------
;; Views

(defn button [{:keys [style on-click text disabled]}]
  [:button {:style (merge {:width 120} style) :on-click on-click :disabled disabled} text])

(defn lbl-input
  [{:keys [style label state-key on-change]}]
  [:div {:style style} label
   [:input {:type :text :style {:margin-left "10px"}
            :default-value (state-key @state)
            :on-change #(do (swap! state assoc state-key (.. % -target -value))
                            (on-change %))}]])

(defn bad-token? []
  (or (:bad-token @state)
      (str/blank? (:token @state))))

(defn main-page []
  [:div {:style (merge flex-v
                       {:background "lightgrey"
                        :border-radius 10})}
   [:h3 {:style child} "22nd ward directory generator"]
   [:a {:style child :href "https://airtable.com/tblOSSpjahPdE2ZQ8/viwNb7TTwWeQ66NaC"}
    "View data on Airtable"]
   [lbl-input {:style child :label "Airtable token:" :state-key :token
               :on-change #(swap! state dissoc :bad-token)}]
   [:input {:style child :type "file" :on-change
            #(swap! state assoc :file (-> % .-target .-files (aget 0)))}]
   [button {:style child :on-click air/synch! :text "Sync to airtable"
            :disabled (or (bad-token?) (nil? (:file @state)))}]
   (let [status (:pdf-status @state)]
     (list
       [:div
        [button {:style child :on-click air/pdf :text "Generate pdf"
                 :disabled (or (bad-token?) (:updating status))}]
        (when (and (:secret status) (not (:updating status)))
          [:a {:style child :href (str "/report/" (:secret status))} "View pdf"])]
       (when-let [last-updated (:last-updated status)]
         [:div {:style (merge child small)}
          (str "pdf last updated: " (js/Date. last-updated))])
       (when (:bad-token @state)
         [:div {:style (merge child {:color "red"})} "Incorrect token"])
       (when-let [status (:status @state)]
         [:div {:style child} status])
       (when (:updating status)
         [:div {:style child} "Generating pdf..."])))])

;; -------------------------
;; Routes

(defonce page (atom #'main-page))

(defn current-page []
  [:div [@page]])

(secretary/defroute "/" []
  (reset! page #'main-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root)
  (air/start-polling!))
