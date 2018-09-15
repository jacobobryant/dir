(ns dir.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.logger :refer [wrap-with-logger]]))

(defn wrap-middleware [handler]
  (-> handler
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      wrap-with-logger
      wrap-edn-params
      wrap-exceptions
      wrap-reload))
