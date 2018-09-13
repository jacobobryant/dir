(ns dir.pdf
  (:require [hiccup.core :refer [html]]
            [ring.util.response :as ring-resp]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

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

(defn make-pdfs [html-filenames pdf-filenames]
  (let [args (flatten (map (fn [html pdf]
                             ["--url" (str "file://" html) "--pdf" pdf])
                           html-filenames pdf-filenames))]
    (spy (apply sh "chrome-headless-render-pdf" "--paper-width" "5.5"
                "--paper-height" "8.5" "--include-background" "--no-margins"
                args))))

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

(defn generate [apts-members]
  (let [base-filenames (map (fn [[apt _]]
                         (str tmpdir (if (str/blank? apt)
                                       "orphans"
                                       (str/replace apt #"[ #]" ""))))
                       apts-members)
        [html-filenames pdf-filenames]
        (map (fn [ext] (map #(str % ext) base-filenames)) [".html" ".pdf"])]
    (doseq [[filename [apt members]] (map vector html-filenames apts-members)]
      (spit filename (html (html-for apt members))))
    (make-pdfs html-filenames pdf-filenames)
    (unite pdf-filenames)))

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
  (swap! state assoc :members (->> members (group-by :apt) (sort-by apt-sort)))
  (when-not (:updating @state)
    (swap! state assoc :updating true)
    (future
      (log/info "updating pdf")
      (->> members
           (group-by :apt)
           (sort-by apt-sort)
           generate)
      (swap! state assoc :updating false :last-updated (System/currentTimeMillis))
      (log/info "done updating pdf")))
  (pdf-status))

(defn view-pdf []
  (ring-resp/file-response pdf-path))
