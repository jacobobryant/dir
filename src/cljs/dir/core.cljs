(ns dir.core
    (:require [reagent.core :as reagent :refer [atom]]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [dir.air :as air]
              [dir.state :refer [state]]
              [clojure.string :as str]))

;; -------------------------
;; Views

(defn button [{:keys [on-click text disabled]}]
  [:button {:style {:width "120px"} :on-click on-click :disabled disabled} text])

(defn lbl-input
  ([label state-key] (lbl-input label state-key nil))
  ([label state-key callback]
  [:div label [:input {:type :text
                       :style {:margin-left "10px"}
                       :default-value (state-key @state)
                       :on-change #(do (swap! state assoc state-key (.. % -target -value))
                                       (and callback (callback %)))}]]))

(defn bad-token? []
  (or (:bad-token @state)
      (str/blank? (:token @state))))

(defn main-page []
  [:div.flex-v
   [:h3 "22nd ward directory generator"]
   [:a {:href "https://airtable.com/tblOSSpjahPdE2ZQ8/viwNb7TTwWeQ66NaC"}
    "View data on Airtable"]
   [lbl-input "Airtable token:" :token #(swap! state dissoc :bad-token)]
   [:input {:type "file" :on-change
            #(swap! state assoc :file (-> % .-target .-files (aget 0)))}]

   [button {:on-click air/synch :text "Sync to airtable"
            :disabled (or (bad-token?) (nil? (:file @state)))}]

   [button {:on-click air/pdf :text "Generate pdf"
            :disabled (bad-token?)}]

   [:div (:status @state)]
   (if (:bad-token @state)
     [:div {:style {:color "red"}} "Incorrect token"])])

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
  (mount-root))
