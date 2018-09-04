(ns dir.pdf
  (:require [hiccup.core :refer [html]]
            [ring.util.response :as ring-resp]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn append [coll x]
  (if (vector? coll)
    (conj coll x)
    (concat coll (list x))))

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
      height: 188px;
      width: 141px;
      margin-bottom: 10px;
      margin-right: 10px;
  }
  .info {
      font-size: 1.2em;
      flex-grow: 1;
  }
  .name {
      font-size: 2em;
  }
  hr {
      color: black;
  }
  .info-line {
      font-size: 1.2em;
      margin-bottom: 10px;
  }")

(defn make-page-pdf [html filename]
  (sh "wkhtmltopdf" "--page-size" "A5" "-" filename :in html))

(defn make-page [[apt members]]
  (let [filename (str tmpdir (if (str/blank? apt) "orphans" apt))]
    (make-page-pdf
      (html
        [:html
         [:body [:head [:style css]]
          [:div.apt apt]
          (for [m members]
            [:div.listing
             [:img {:src (:picture m)}]
             [:div.info
              [:div.name (some m [:override-name :lds-tools-name])]
              [:hr]
              [:div.phone.info-line (:phone m)]
              [:div.email.info-line (:email m)]
              [:div.birthday.info-line "Birthday: " (:birthday m)]]])]])
      filename)
    filename))

(defn unite [pages]
  (apply sh "pdfunite" (append pages pdf-path)))

(defn apt-sort [[apt _]]
  (if (some #(str/includes? apt %) ["Park Plaza" "Stonebridge"])
    apt
    "zzzz"))

(defn gen-pdf [members]
  (let [sorted (sort-by apt-sort (group-by :apt members))
        pages (pmap make-page sorted)]
    (unite pages)
    ""))

(defn view-pdf []
  (ring-resp/file-response pdf-path))
