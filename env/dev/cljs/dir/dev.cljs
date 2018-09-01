(ns ^:figwheel-no-load dir.dev
  (:require
    [dir.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
