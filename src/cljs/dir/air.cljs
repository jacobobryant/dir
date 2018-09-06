(ns dir.air
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! chan timeout]]
            [goog.labs.format.csv :as csv]
            [clojure.string :as str]
            [dir.state :refer [state]]))

(def table-url "https://api.airtable.com/v0/appg6cAHwO53N6Apb/22nd%20ward%20directory")

(defn slurp [file]
  (let [out (chan)
        reader (js/FileReader.)]
    (set! (.-onload reader) #(go (>! out (.. % -target -result))))
    (.readAsText reader file)
    out))

(defn format-name
  "O'Bryant, Jacob John -> Jacob O'Bryant"
  [raw-name]
  (if (str/includes? raw-name ",")
    (let [[given-names last-name] (reverse (str/split raw-name ", "))
          first-name (first (str/split given-names " "))]
      (str first-name " " last-name))
    raw-name))

(defn get-first [& stuff]
  (some #(and % (not-empty (str/trim %))) stuff))

(defn parse-apt
  [address]
  (let [apt-num (nth (re-find #"(#|Apt |Stonebridge )(\w+)" address) 2)
        complex (if (str/includes? address "910 N") "Park Plaza" "Stonebridge")]
    (if apt-num
      (str complex " #" apt-num)
      "No apartment listed")))

(defn parse-member [[family-name
                     couple-name
                     family-phone
                     family-email
                     family-address
                     head-name
                     head-phone
                     head-email]]
  {:lds-tools-name (format-name (get-first head-name couple-name family-name))
   :email (get-first head-email family-email)
   :phone (get-first head-phone family-phone)
   :apt (parse-apt family-address)
   :in-lds-tools true})

(defn now []
  (.getTime (js/Date.)))

(defn air-request [method url params]
  (go
    (when-not (:bad-token @state)
      (let [exec-time (:last-req (swap! state update :last-req
                                        #(if (nil? %) (now) (max (now) (+ % 220)))))
            _ (<! (timeout (max 0 (- exec-time (now))))) ; rate limiting
            params (merge {:with-credentials? false
                           :oauth-token (:token @state)}
                          (if (= method http/get)
                            {:query-params params}
                            {:json-params {:fields params}}))
            response (<! (method url params))]
        (when (= (:status response) 401)
          (swap! state assoc :bad-token true))
        response))))

(defn get-air-members
  ([] (get-air-members nil))
  ([offset]
   (go (let [params (and offset {:offset offset})
             response (<! (air-request http/get table-url params))
             members (into {} (map (fn [record] [(get-in record [:fields :lds-tools-name]) record])
                                   (get-in response [:body :records])))]
         (if-let [next-offset (get-in response [:body :offset])]
           (let [next-batch (<! (get-air-members next-offset))]
             (merge next-batch members))
           members)))))

(defn record-url
  [airtable-id]
  (str table-url "/" airtable-id))

(defn update-member!
  [record airtable-id]
  (air-request http/patch (record-url airtable-id) record))

(defn add-member!
  [record]
  (air-request http/post table-url record))

(defn hide-member!
  [airtable-id]
  (air-request http/patch (record-url airtable-id) {:in-lds-tools false}))

(defn synch! [e]
  (when-let [file (:file @state)]
    (swap! state assoc :status "Syncing...")
    (go
      (let [csv-members (->> (<! (slurp file))
                             csv/parse js->clj rest
                             (map parse-member)
                             (map (fn [record] [(:lds-tools-name record) record]))
                             (into {}))
            air-members (<! (get-air-members))
            actions (concat
                      (for [[lds-name record] csv-members]
                        (if-let [airtable-id (get-in air-members [lds-name :id])]
                          (update-member! record airtable-id)
                          (add-member! record)))
                      (filter identity
                              (for [[lds-name air-record] air-members]
                                (when-not (contains? csv-members lds-name)
                                  (hide-member! (:id air-record))))))]
        (doseq [a actions] (<! a))
        (swap! state assoc :status "Done syncing")))))

(defn pdf []
  (swap! state assoc :status "Generating pdf...")
  (go (let [payload (->> (<! (get-air-members))
                         (map #(get-in % [1 :fields]))
                         (filter :in-lds-tools))
            response (<! (http/post "/gen-report"
                                    {:edn-params {:members payload}}))]
        (swap! state assoc :status "Done generating pdf")
        (set! (.-location js/window) "/report"))))
