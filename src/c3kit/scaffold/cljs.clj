(ns c3kit.scaffold.cljs
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.util :as util]
    [c3kit.apron.utilc :as utilc]
    [cljs.build.api :as api]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (cljs.closure Compilable Inputs)
           (com.microsoft.playwright ConsoleMessage Playwright)
           (java.io File)
           (java.net URL)
           (java.util.function Consumer)))

(defonce build-config (atom nil))
(defonce run-env (atom {}))
(defonce ns-prefix (atom nil))
(defonce ignore-errors (atom []))
(defonce ignore-consoles (atom []))
(defonce errors (atom []))

(defonce red "\u001B[31m")
(defonce default-color "\u001b[0m")

(deftype FnConsumer [accept-fn]
  Consumer
  (accept [_this thing] (accept-fn thing)))

(def speclj-defaults
  {:color     true
   :reporters ["documentation"]})

(defn config-with-defaults [config]
  (cond-> speclj-defaults
          (map? config)
          (merge config)))

(defn build-spec-config []
  (->> (config-with-defaults (:specs @build-config))
       (map (fn [[k v]] (str (utilc/->json k) ", " (utilc/->json v))))
       (str/join ", ")))

(defn build-js-path []
  (str (.toURL (.toURI (io/file (:output-to @build-config))))))

(defn build-spec-html []
  (-> (slurp (io/resource "c3kit/scaffold/specs.html"))
      (str/replace "<--OUTPUT-TO-->" (build-js-path))
      (str/replace "/*{SPEC-CONFIG}*/" (build-spec-config))))

(defn spec-html-url []
  (let [output-dir     (:output-dir @build-config)
        spec-html-file (io/file output-dir "specs.html")]
    (when-not (.exists spec-html-file)
      (spit spec-html-file (build-spec-html)))
    (str (.toURL spec-html-file))))

(defn project-ns? [ns] (str/starts-with? ns @ns-prefix))

(defn build-reverse-deps [deps]
  (let [ns->file     (get deps "idToPath_")
        project-nses (filter project-ns? (keys ns->file))
        rdeps        (reduce #(assoc %1 %2 #{}) {} project-nses)]
    (reduce (fn [result ns]
              (let [file             (get-in deps ["idToPath_" ns])
                    requires         (get-in deps ["dependencies_" file "requires"])
                    project-requires (filter project-ns? requires)]
                (reduce (fn [result rdep] (update result rdep (fn [ns-set] (set (conj ns-set ns)))))
                        result project-requires)))
            rdeps project-nses)))

(defn ^File timestamp-file []
  (let [output-dir (:output-dir @build-config)]
    (io/file output-dir ".specljs-timestamp")))

(defn timestamp! [file]
  (if (.exists file)
    (.setLastModified file (System/currentTimeMillis))
    (spit file "")))

(defn modified-time [file]
  (if (.exists file)
    (.lastModified file)
    0))

(defn find-updated-specs [rdeps deps timestamp]
  (let [ns->file (get deps "idToPath_")]
    (filter (fn [ns] (let [mod-time (-> (get ns->file ns) URL. .toURI File. .lastModified)]
                       (> mod-time timestamp)))
            (keys rdeps))))

(defn rdeps-affected-by [rdeps updated]
  (let [all-rdeps (mapcat #(get rdeps %) updated)]
    (if (seq all-rdeps)
      (concat updated (rdeps-affected-by rdeps all-rdeps))
      updated)))

(defn build-spec-map [rdeps deps timestamp]
  (->> (find-updated-specs rdeps deps timestamp)
       (rdeps-affected-by rdeps)
       (filter #(str/ends-with? % "_spec"))
       (map #(str/replace % "_" "-"))
       (reduce #(assoc %1 %2 true) {})))

(defn- with-red [s]
  (str red s default-color))

(defn print-error-summary [{:keys [exit-if-errors?] :as settings}]
  (when (seq @errors)
    (println (with-red "Some specs may not be running because errors were found:"))
    (run! println @errors)
    (if exit-if-errors?
      (System/exit -1))))

(defn run-specs-auto [page timestamp]
  (let [deps     (.evaluate page "goog.debugLoader_")
        rdeps    (build-reverse-deps deps)
        spec-map (build-spec-map rdeps deps timestamp)
        js       (str "runSpecsFiltered(" (utilc/->json spec-map) ")")]
    (if (seq spec-map)
      (do (println "Only running affected specs:")
          (doseq [ns (sort (keys spec-map))] (println "  " ns))
          (.evaluate page js))
      (println "No specs affected. Skipping run."))
    (print-error-summary {:exit-if-errors? false})))

(defn run-specs-once [page]
  (try
    (let [status (.evaluate page "runSpecsFiltered(null)")]
      (print-error-summary {:exit-if-errors? true})
      (System/exit status))
    (catch Exception e
      (.printStackTrace e)
      (System/exit -1))))

(defn- with-red [s]
  (str red s default-color))

(defn on-error [error]
  (when-not (some #(re-find % error) @ignore-errors)
    (let [msg (with-red (str "ERROR: " error))]
      (swap! errors conj msg)
      (println msg))))

(defn on-console [m]
  (let [^ConsoleMessage message m
        text                    (.text message)]
    (when-not (some #(re-find % text) @ignore-consoles)
      (println text))))

(defn run-specs [& {:keys [timestamp auto?]}]
  (let [browser (-> (Playwright/create)
                    (.chromium)
                    (.launch))
        page    (-> browser
                    (.newContext)
                    (.newPage))]
    (.onPageError page (FnConsumer. on-error))
    (.onConsoleMessage page (FnConsumer. on-console))
    (.navigate page (spec-html-url))
    (if auto?
      (run-specs-auto page timestamp)
      (run-specs-once page))
    (.close browser)))

(defn on-dev-compiled []
  (reset! errors [])
  (let [ts-file   (timestamp-file)
        timestamp (modified-time ts-file)]
    (timestamp! ts-file)
    ;; MDM - Touch the js output file so the browser will reload it without hard refresh.
    (timestamp! (io/file (:output-to @build-config)))
    (run-specs :auto? true :timestamp timestamp)))

(deftype Sources [build-options]
  Inputs
  (-paths [_] (map io/file (:sources build-options)))
  Compilable
  (-compile [_ opts] (mapcat #(cljs.closure/compile-dir (io/file %) opts) (:sources build-options)))
  (-find-sources [_ opts] (mapcat #(cljs.closure/-find-sources % opts) (:sources build-options))))

(defn auto-run [build-options]
  (while true
    (try
      (api/watch (Sources. build-options) build-options)
      (catch Exception e
        (.printStackTrace e)))))

(defn- resolve-watch-fn [options]
  (if-let [watch-fn-sym (:watch-fn options)]
    (do
      (when-not (symbol? watch-fn-sym) (throw (Exception. ":watch-fn must be a fully qualified symbol")))
      (assoc options :watch-fn (util/resolve-var watch-fn-sym)))
    options))

(defn- resolve-build-config [config build-key]
  (let [result (get config build-key)]
    (when (nil? result)
      (throw (ex-info (str "build-key `" build-key "` missing from config") config)))
    result))

(defn configure! [config build-key]
  (when-let [env (:run-env config)] (reset! run-env env))
  (reset! ns-prefix (:ns-prefix config "i.forgot.to.add.ns-prefix.to.cljs.edn"))
  (reset! ignore-errors (map re-pattern (:ignore-errors config [])))
  (reset! ignore-consoles (map re-pattern (:ignore-console config [])))
  (reset! build-config (resolve-watch-fn (resolve-build-config config build-key))))

(defn- ->command [args]
  (let [command (or (first args) "auto")]
    (assert (#{"once" "auto" "spec"} command)
            (str "Unrecognized build command: " command ". Must be 'once', 'auto', or 'spec'"))
    command))

(defn- ->build-key [config]
  (keyword (apply app/find-env (or (:env-keys config) app/env-keys))))

(defn -main
  "Compile clojure script and run specs.
  args can be empty or have the type of command:
    auto (default)  - watch files and recompiling and re-running affected specs
    once            - compile and run specs (if enabled) once
    spec            - just run the specs (assumes compilation is already done)"
  [& args]
  (let [command   (->command args)
        config    (util/read-edn-resource "config/cljs.edn")
        build-key (->build-key config)]
    (configure! config build-key)
    (when-not (= "spec" command)
      (println "Compiling ClojureScript:" command build-key)
      (util/establish-path (:output-to @build-config))
      (io/delete-file ".specljs-timestamp" true))
    (cond (= "once" command) (do (api/build (Sources. @build-config) @build-config)
                                 (when (:specs @build-config) (run-specs)))
          (= "spec" command) (run-specs)
          :else (let [timestamp (timestamp-file)]
                  (println "watching namespaces with prefix:" @ns-prefix)
                  (when (.exists timestamp) (.delete timestamp))
                  (auto-run @build-config)))))
