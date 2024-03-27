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
(defonce browser (atom nil))

(deftype FnConsumer [accept-fn]
  Consumer
  (accept [_this thing] (accept-fn thing)))

(defn spec-html-url []
  (let [output-dir     (:output-dir @build-config)
        spec-html-file (io/file output-dir "specs.html")
        js-file        (str (.toURL (.toURI (io/file (:output-to @build-config)))))]
    (when-not (.exists spec-html-file)
      (let [html (-> (slurp (io/resource "c3kit/scaffold/specs.html"))
                     (str/replace "<--OUTPUT-TO-->" js-file))]
        (spit spec-html-file html)))
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

(defn timestamp!
  ([] (timestamp! (timestamp-file)))
  ([file]
   (if (.exists file)
     (.setLastModified file (System/currentTimeMillis))
     (spit file ""))))

(defn find-updated-specs [rdeps deps]
  (let [^File time-file (timestamp-file)
        min-millis      (if (.exists time-file) (.lastModified time-file) 0)
        ns->file        (get deps "idToPath_")]
    (filter (fn [ns] (let [mod-time (-> (get ns->file ns) URL. .toURI File. .lastModified)]
                       (> mod-time min-millis)))
            (keys rdeps))))

(defn rdeps-affected-by [rdeps updated]
  (let [all-rdeps (mapcat #(get rdeps %) updated)]
    (if (seq all-rdeps)
      (concat updated (rdeps-affected-by rdeps all-rdeps))
      updated)))

(defn run-specs-auto [page]
  (let [deps     (.evaluate page "goog.debugLoader_")
        rdeps    (build-reverse-deps deps)
        updated  (find-updated-specs rdeps deps)
        affected (rdeps-affected-by rdeps updated)
        spec-map (->> (filter #(str/ends-with? % "_spec") affected)
                      (map #(str/replace % "_" "-"))
                      (reduce #(assoc %1 %2 true) {}))
        js       (str "runSpecsFiltered(" (utilc/->json spec-map) ")")]
    (if (seq spec-map)
      (do (println "Only running affected specs:")
          (doseq [ns (sort (keys spec-map))] (println "  " ns))
          (.evaluate page js))
      (println "No specs affected. Skipping run."))
    (timestamp!)))

(defn run-specs-once [page]
  (try
    (System/exit (.evaluate page "runSpecsFiltered(null)"))
    (catch Exception e
      (.printStackTrace e)
      (System/exit 1))))

(defn on-error [error]
  (when-not (some #(re-find % error) @ignore-errors)
    (println "ERROR:" error)))

(defn on-console [m]
  (let [^ConsoleMessage message m
        text                    (.text message)]
    (when-not (some #(re-find % text) @ignore-consoles)
      (println text))))

(defn run-specs [auto?]
  (let [context    (.newContext @browser)
        page       (.newPage context)]
    (.onPageError page (FnConsumer. on-error))
    (.onConsoleMessage page (FnConsumer. on-console))
    (.navigate page (spec-html-url))
    (if auto?
      (run-specs-auto page)
      (run-specs-once page))))

(defn on-dev-compiled []
  ;; MDM - Touch the js output file so the browser will reload it without hard refresh.
  (timestamp! (io/file (:output-to @build-config)))
  (run-specs true))

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

(defn -main
  "Compile clojure script and run specs.
  args can be empty or have the type of command:
    auto (default)  - watch files and recompiling and re-running affected specs
    once            - compile and run specs (if enabled) once
    spec            - just run the specs (assums compilcation is already done)"
  [& args]
  (let [command   (or (first args) "auto")
        config    (util/read-edn-resource "config/cljs.edn")
        build-key (keyword (apply app/find-env (or (:env-keys config) app/env-keys)))]
    (when-let [env (:run-env config)] (reset! run-env env))
    (reset! ns-prefix (:ns-prefix config "i.forgot.to.add.ns-prefix.to.cljs.edn"))
    (reset! ignore-errors (map re-pattern (:ignore-errors config [])))
    (reset! ignore-consoles (map re-pattern (:ignore-console config [])))
    (reset! build-config (resolve-watch-fn (get config build-key)))
    (reset! browser (-> (Playwright/create) .chromium .launch))
    (assert (#{"once" "auto" "spec"} command) (str "Unrecognized build command: " command ". Must be 'once', 'auto', or 'spec'"))
    (when-not (= "spec" command)
      (println "Compiling ClojureScript:" command build-key)
      (util/establish-path (:output-to @build-config))
      (io/delete-file ".specljs-timestamp" true))
    (cond (= "once" command) (do (api/build (Sources. @build-config) @build-config)
                                 (when (:specs @build-config) (run-specs false)))
          (= "spec" command) (run-specs false)
          :else (let [timestamp (timestamp-file)]
                  (println "watching namespaces with prefix:" @ns-prefix)
                  (when (.exists timestamp) (.delete timestamp))
                  (auto-run @build-config)))))
