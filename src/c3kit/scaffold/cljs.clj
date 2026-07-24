(ns c3kit.scaffold.cljs
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.util :as util]
    [c3kit.apron.utilc :as utilc]
    [cljs.build.api :as api]
    [cljs.closure]
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
(defonce running (volatile! true))

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

(defn project-ns? [ns-id] (str/starts-with? ns-id @ns-prefix))

(defn build-reverse-deps [deps]
  (let [ns->file     (get deps "idToPath_")
        project-nses (filter project-ns? (keys ns->file))
        rdeps        (reduce #(assoc %1 %2 #{}) {} project-nses)]
    (reduce (fn [result ns-id]
              (let [file             (get-in deps ["idToPath_" ns-id])
                    requires         (get-in deps ["dependencies_" file "requires"])
                    project-requires (filter project-ns? requires)]
                (reduce (fn [result rdep] (update result rdep (fn [ns-set] (set (conj ns-set ns-id)))))
                        result project-requires)))
            rdeps project-nses)))

(defn timestamp-file ^File []
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
    (filter (fn [ns-id] (let [mod-time (-> (get ns->file ns-id) URL. .toURI File. .lastModified)]
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

(defn print-error-summary [{:keys [exit-if-errors?]}]
  (when (seq @errors)
    (println (with-red "Some specs may not be running because errors were found:"))
    (run! println @errors)
    (when exit-if-errors?
      (System/exit -1))))

(defn run-specs-auto [page timestamp]
  (let [deps     (.evaluate page "goog.debugLoader_")
        rdeps    (build-reverse-deps deps)
        spec-map (build-spec-map rdeps deps timestamp)
        js       (str "runSpecsFiltered(" (utilc/->json spec-map) ")")]
    (if (seq spec-map)
      (do (println "Only running affected specs:")
          (doseq [ns-id (sort (keys spec-map))] (println "  " ns-id))
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

(defn create-pw-resources []
  (let [pw      (Playwright/create)
        browser (-> pw (.chromium) (.launch))
        page    (-> browser (.newContext) (.newPage))]
    {:playwright pw :browser browser :page page}))

(defn run-specs
  "Launches Playwright, navigates to the generated specs.html, and runs the
  ClojureScript Speclj suite. With `:auto? true`, only specs affected by
  changes since `:timestamp` are run; otherwise, runs the full suite and
  exits the JVM with the Speclj status code. Always closes browser and
  Playwright resources on exit."
  [& {:keys [timestamp auto?]}]
  (let [{:keys [playwright browser page]} (create-pw-resources)]
    (try
      (.onPageError page (FnConsumer. on-error))
      (.onConsoleMessage page (FnConsumer. on-console))
      (.navigate page (spec-html-url))
      (if auto?
        (run-specs-auto page timestamp)
        (run-specs-once page))
      (finally
        (.close browser)
        (.close playwright)))))

(defn on-dev-compiled
  "Watch-fn callback for ClojureScript auto-compilation. Resets the error
  atom, touches the `.specljs-timestamp` file, touches the JS output file
  so the browser reloads it, then re-runs only the affected specs."
  []
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

(defn shutdown!
  "Sets `running` to false and interrupts the given thread. Used by both
  the JVM shutdown hook and the stdin monitor to stop the auto-run loop."
  [main-thread]
  (vreset! running false)
  (.interrupt main-thread))

(defn install-shutdown-hook!
  "Registers a JVM shutdown hook that calls `shutdown!` on the current
  thread, so `kill`/Ctrl-C/parent-process-exit gracefully stops the
  auto-run loop."
  []
  (let [main-thread (Thread/currentThread)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(shutdown! main-thread)))))

(defn monitor-stdin!
  "Spawns a daemon thread that reads stdin until EOF, then calls
  `shutdown!`. Prevents orphaned subprocesses when the parent process
  closes stdin (e.g. agent harnesses that don't propagate signals)."
  []
  (let [main-thread (Thread/currentThread)]
    (doto (Thread.
            (fn []
              (try
                (while (not= -1 (.read System/in)))
                (catch Exception _))
              (shutdown! main-thread)))
      (.setDaemon true)
      (.start))))

(defn auto-run
  "Runs `cljs.build.api/watch` in a loop while the `running` volatile is
  true. Exceptions thrown by `api/watch` are printed and the loop restarts;
  exceptions raised after a shutdown signal are logged briefly and dropped
  to keep shutdown clean."
  [build-options]
  (while @running
    (try
      (api/watch (Sources. build-options) build-options)
      (catch Exception e
        (if @running
          (.printStackTrace e)
          (println "auto-run: ignoring exception during shutdown:" (.getMessage e)))))))

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

(defn configure!
  "Reads the EDN config map and the chosen build-key (e.g. :development) and
  populates the runtime atoms (`build-config`, `run-env`, `ns-prefix`,
  `ignore-errors`, `ignore-consoles`). Throws via `resolve-build-config` if
  the build-key is missing, and throws if `:ns-prefix` is missing.

  Called once from `-main` before compilation."
  [config build-key]
  (when-let [env (:run-env config)] (reset! run-env env))
  (when-not (:ns-prefix config)
    (throw (ex-info ":ns-prefix is required in config/cljs.edn" {:config config})))
  (reset! ns-prefix (:ns-prefix config))
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
      (io/delete-file (timestamp-file) true))
    (cond (= "once" command) (do (api/build (Sources. @build-config) @build-config)
                                 (when (:specs @build-config) (run-specs)))
          (= "spec" command) (run-specs)
          :else (let [timestamp (timestamp-file)]
                  (println "watching namespaces with prefix:" @ns-prefix)
                  (when (.exists timestamp) (.delete timestamp))
                  (install-shutdown-hook!)
                  (monitor-stdin!)
                  (auto-run @build-config)))))
