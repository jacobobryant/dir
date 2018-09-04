(ns dir.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.edn :refer [wrap-edn-params]]))

(defn wrap-middleware [handler]
  (-> handler
    (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
    wrap-edn-params))
