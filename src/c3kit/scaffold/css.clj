(ns c3kit.scaffold.css
  (:require
    [c3kit.apron.util :as util]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.namespace.dir :as track]
    [clojure.tools.namespace.reload :as reload]
    [garden.core :as garden]
    ))

(defmacro print-exec-time
  [tag expr]
  `(let [start# (. System (nanoTime))
         ret#   ~expr]
     (println (str ~tag ": " (/ (double (- (. System (nanoTime)) start#)) 1000000000.0) " secs"))
     ret#))

(defn- generate-css [config]
  (let [output  (:output-file config)
        css-var (util/resolve-var (:var config))
        css     (garden/css (:flags config) @css-var)]
    (println (str "css: writing " (count css) " bytes to " output))
    (spit (io/file output) css)))

(defn- generate [config]
  (print-exec-time "generating css" (generate-css config))
  (println))

(defn handle-error [error]
  (let [{:keys [cause via]} (Throwable->map error)]
    (println "ERROR ---------------------------------------------")
    (println "Cause: " cause)
    (pprint via)
    (println "---------------------------------------------------")
    (println)))

(defn auto-generate [config]
  (loop [tracker {} last-mod-time 0]
    (let [tracker  (track/scan tracker (:source-dir config))
          mod-time (:clojure.tools.namespace.dir/time tracker 0)
          change?  (> mod-time last-mod-time)
          to-load  (seq (:clojure.tools.namespace.track/load tracker))]
      (if (and change? to-load)
        (let [_       (println "reloading: " to-load)
              tracker (print-exec-time "reloading time" (reload/track-reload tracker))]
          (if-let [error (:clojure.tools.namespace.reload/error tracker)]
            (handle-error error)
            (generate config))
          (recur tracker mod-time))
        (do
          (Thread/sleep 1000)
          (recur tracker mod-time))))))

(defn- ->once-or-auto [args]
  (let [once-or-auto (or (first args) "auto")]
    (assert (#{"once" "auto"} once-or-auto) (str "Unrecognized build frequency: " once-or-auto ". Must be 'once' or 'auto'"))
    once-or-auto))

(defn -main [& args]
  (let [once-or-auto (->once-or-auto args)
        config       (util/read-edn-resource "config/css.edn")]
    (util/establish-path (:output-file config))
    (println "Compiling CSS:" once-or-auto)
    (if (= "once" once-or-auto)
      (generate config)
      (auto-generate config))))
