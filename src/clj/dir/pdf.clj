(ns dir.pdf
  (:require [hiccup.core :refer [html]]
            [ring.util.response :as ring-resp]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

; TODO handle pages with >4 people

(defonce state (atom {:updating false
                      :last-updated nil}))

(defn append [coll x]
  (if (vector? coll)
    (conj coll x)
    (concat coll [x])))

(def tmpdir "/tmp/")

(def pdf-path "/tmp/directory.pdf")

(def css "
  body {
      font-family: Arial, sans-serif;
      margin: 0.5in;
  }
  .apt {
      font-size: 2em;
      background: grey;
      color: white;
      margin-top: 10px;
      margin-bottom: 10px;
      padding: 5px;
  }
  .listing {
      display: flex;
      flex-direction: row;
  }
  img {
      height: 155px;
      width: 116px;
      margin-bottom: 10px;
      margin-right: 10px;
      object-fit: cover;
  }
  .info {
      font-size: 1.1em;
      flex-grow: 1;
  }
  .name {
      font-size: 1.4em;
  }
  hr {
      color: black;
  }
  .info-line {
      margin-bottom: 5px;
  }")

(defn spy [& args]
  (let [prefix (apply str (butlast args))
        result (last args)
        fmt (str prefix (when-not (empty? prefix) ": ") "%s")]
   (log/spyf :info fmt result)))

(defn make-page-pdf [html filename]
  (spit (str filename ".html") html)
  ; TODO see if this command can generate the whole pdf in one go
  (spy (sh "chrome-headless-render-pdf" "--paper-width" "5.5" "--paper-height" "8.5"
           "--include-background" "--no-margins" "--url" (str "file://" filename ".html") "--pdf" filename)))

(defn html-for [apt members]
  [:html
   [:body [:head [:style css]]
    [:div.apt apt]
    (for [m members]
      [:div.listing
       [:img {:src (-> m :picture first :url)}]
       [:div.info
        [:div.name (some m [:override-name :lds-tools-name])]
        [:hr]
        [:div.phone.info-line (:phone m)]
        [:div.email.info-line (:email m)]
        (when-let [b (:birthday m)]
          [:div.birthday.info-line "Birthday: " b])]])]])

(defn make-page [[apt members]]
  (let [filename (str tmpdir (if (str/blank? apt) "orphans" (str/replace apt #"[ #]" "")))]
    (make-page-pdf (html (html-for apt members)) filename)
    filename))

(defn unite [pages]
  (let [args (append pages pdf-path)]
    (spy "pdfunite " args (apply sh "pdfunite" args))))

(defn apt-sort [[apt _]]
  (if (some #(str/includes? apt %) ["Park Plaza" "Stonebridge"])
    apt
    "zzzz"))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn pdf-status []
  (edn-response (spy "pdf status" @state)))

(defn gen-pdf! [members]
  (when-not (:updating @state)
    (swap! state assoc :updating true)
    (future
      (log/info "updating pdf")
      (->> members
           (group-by :apt)
           (sort-by apt-sort)
           (map make-page)
           unite)
      (swap! state assoc :updating false :last-updated (System/currentTimeMillis))
      (log/info "done updating pdf")))
  (pdf-status))

(defn view-pdf []
  (ring-resp/file-response pdf-path))
