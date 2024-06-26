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

(defn on-dev-compiled [config]
  (when-let [on-css-compiled (:on-css-compiled config)]
    (on-css-compiled config)))

(defn- generate-css [config]
  (let [output  (:output-file config)
        css-var (util/resolve-var (:var config))
        css     (garden/css (:flags config) @css-var)]
    (println (str "css: writing " (count css) " bytes to " output))
    (spit (io/file output) css)))

(defn- generate [config]
  (print-exec-time "generating css" (generate-css config))
  (on-dev-compiled config)
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

(defn resolve-on-css-compiled [options]
  (if-let [on-compiled-sym (:on-css-compiled options)]
    (do
      (when-not (symbol? on-compiled-sym) (throw (Exception. ":on-css-compiled must be a fully qualified symbol")))
      (assoc options :on-css-compiled (util/resolve-var on-compiled-sym)))
    options))

(defn- ->once-or-auto [args]
  (let [once-or-auto (or (first args) "auto")]
    (assert (#{"once" "auto"} once-or-auto) (str "Unrecognized build frequency: " once-or-auto ". Must be 'once' or 'auto'"))
    once-or-auto))

(defn -main [& args]
  (let [once-or-auto (->once-or-auto args)
        config       (-> (util/read-edn-resource "config/css.edn") resolve-on-css-compiled)]
    (util/establish-path (:output-file config))
    (println "Compiling CSS:" once-or-auto)
    (if (= "once" once-or-auto)
      (generate config)
      (auto-generate config))))
