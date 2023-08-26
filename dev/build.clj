(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.java.shell :as shell]
            [cemerick.pomegranate.aether :as aether]))

(def lib-name "scaffold")
(def basis (b/create-basis {:project "deps.edn"}))
(def src-dirs (:paths basis))
(def lib (symbol "com.cleancoders.c3kit" lib-name))
(def version (str/trim (slurp "VERSION")))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" lib-name version))

(defn clean [_]
  (println "cleaning")
  (b/delete {:path "target"}))

(defn pom [_]
  (println "writing pom.xml")
  (b/write-pom {:basis basis
                :class-dir class-dir
                :lib lib
                :version version}))

(defn jar [_]
  (clean nil)
  (pom nil)
  (println "building" jar-file)
  (b/copy-dir {:src-dirs src-dirs
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn tag [_]
  (let [clean? (str/blank? (:out (shell/sh "git" "diff")))
        tags   (delay (->> (shell/sh "git" "tag") :out str/split-lines set))]
    (cond (not clean?) (do (println "ABORT: commit master before tagging") (System/exit 1))
          (contains? @tags version) (println "tag already exists")
          :else (do (println "pushing tag" version)
                    (shell/sh "git" "tag" version)
                    (shell/sh "git" "push" "--tags")))))

(defn deploy [_]
  (tag nil)
  (jar nil)
  (aether/deploy {:coordinates [lib version]
                  :jar-file jar-file
                  :repository {"clojars" {:url "https://clojars.org/repo"
                                          :username (System/getenv "CLOJARS_USERNAME")
                                          :password (System/getenv "CLOJARS_PASSWORD")}}
                  :transfer-listener :stdout}))
