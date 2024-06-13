(ns c3kit.scaffold.cljs-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.util :as util]
            [c3kit.scaffold.cljs :as sut]
            [cljs.build.api :as api]
            [clojure.java.io :as io]
            [speclj.core :refer :all])
  (:import (java.lang AssertionError)))

(def config {:run-cmd     "command"
             :run-env     "environment"
             :env-keys    ["ENV" ".env"]
             :development {:output-dir "tmp"
                           :output-to  "tmp/out.cljs"
                           :specs      {:color     true
                                        :reporters ["documentation"]
                                        :tags      ["~one" "two"]}}
             :defaults    {:specs      true
                           :output-dir "tmp"
                           :output-to  "tmp/out.cljs"}
             :no-color    {:specs      {:color nil}
                           :output-dir "tmp"
                           :output-to  "tmp/out.cljs"}})

(def default-color "\u001b[0m")
(def red "\u001B[31m")

(describe "CLJS Compilation"
  (with-stubs)

  (redefs-around [println ccc/noop
                  api/build (stub :cljs/build)
                  sut/run-specs (stub :run-specs)])

  (before (.mkdir (io/file "tmp"))
          (reset! sut/build-config nil)
          (reset! sut/run-env {})
          (reset! sut/ns-prefix nil)
          (reset! sut/ignore-errors [])
          (reset! sut/ignore-consoles []))

  (after (io/delete-file "tmp/out.cljs" true)
         (io/delete-file "tmp/.specljs-timestamp" true))

  (context "build-spec-html"

    (it "writes javascript output path"
      (sut/configure! config :development)
      (let [html (sut/build-spec-html)
            js-path (str (.toURL (.toURI (io/file "tmp/out.cljs"))))
            script-tag (str "script src=\"" js-path "\"")]
        (should-contain script-tag html)))

    (it "writes spec config"
      (sut/configure! config :development)
      (let [html (sut/build-spec-html)
            config "run_specs(\"color\", true, \"reporters\", [\"documentation\"], \"tags\", [\"~one\",\"two\"])"]
        (should-contain config html)))

    (it "color is nil"
      (sut/configure! config :no-color)
      (let [html (sut/build-spec-html)
            config "run_specs(\"color\", null, \"reporters\", [\"documentation\"])"]
        (should-contain config html)))

    (it "defaults to color true and documentation reporter"
      (sut/configure! config :defaults)
      (let [html (sut/build-spec-html)
            config "run_specs(\"color\", true, \"reporters\", [\"documentation\"])"]
        (should-contain config html)))

    )

  (context "on-error"
    (before (reset! sut/errors []))
    (redefs-around [println (stub :println)])

    (it "prints error if not ignored"
      (sut/on-error "this is an error")
      (should-have-invoked :println {:with [(str red "ERROR: this is an error" default-color)]})
      (sut/on-error "another error")
      (should-have-invoked :println {:with [(str red "ERROR: another error" default-color)]}))

    (it "doesn't print error if ignored"
      (swap! sut/ignore-errors conj #"hello")
      (sut/on-error "hello")
      (should-not-have-invoked :println)
      (swap! sut/ignore-errors conj #"greetings")
      (sut/on-error " greetings")
      (sut/on-error "hello")
      (sut/on-error "bye")
      (should-have-invoked :println {:with [(str red "ERROR: bye" default-color)]}))

    (it "saves error"
      (sut/on-error "this is an error")
      (should= [(str red "ERROR: this is an error" default-color)] @sut/errors)
      (sut/on-error "errors are cool")
      (should= [(str red "ERROR: this is an error" default-color) (str red "ERROR: errors are cool" default-color)] @sut/errors)))

  (context "on-dev-compiled"

    (it "no timestamp file"
      (sut/configure! config :development)
      (sut/on-dev-compiled)
      (should (.exists (io/file "tmp/out.cljs")))
      (should (.exists (io/file "tmp/.specljs-timestamp")))
      (should> (.lastModified (io/file "tmp/.specljs-timestamp")) 0)
      (should-have-invoked :run-specs {:with [:auto? true :timestamp 0]}))

    (it "timestamp file exists"
      (sut/configure! config :development)
      (let [ts-file (io/file "tmp/.specljs-timestamp")]
        (spit ts-file "")
        (.setLastModified ts-file 1234)
        (sut/on-dev-compiled)
        (should-not= 1234 (.lastModified ts-file))
        (should-have-invoked :run-specs {:with [:auto? true :timestamp 1234]})))

    (it "resets error atom"
      (reset! sut/errors ["Hello"])
      (sut/configure! config :development)
      (sut/on-dev-compiled)
      (should= [] @sut/errors)))

  (context "main"
    (redefs-around [util/read-edn-resource (constantly config)
                    util/establish-path (stub :establish-path)
                    io/delete-file (stub :delete-file)
                    sut/auto-run (stub :auto-run)])

    (it "runs once"
      (sut/-main "once")
      (should= "environment" @sut/run-env)
      (should= (:development config) @sut/build-config)
      (should-have-invoked :establish-path {:with ["tmp/out.cljs"]})
      (should-have-invoked :delete-file {:with [".specljs-timestamp" true]})
      (should-have-invoked :cljs/build)
      (should-have-invoked :run-specs {:with []})
      (should-not-have-invoked :auto-run))

    (it "runs automatically"
      (sut/-main "auto")
      (should= "environment" @sut/run-env)
      (should= (:development config) @sut/build-config)
      (should-have-invoked :establish-path {:with ["tmp/out.cljs"]})
      (should-have-invoked :delete-file {:with [".specljs-timestamp" true]})
      (should-have-invoked :auto-run {:with [@sut/build-config]})
      (should-not-have-invoked :cljs/build)
      (should-not-have-invoked :run-specs))

    (it "defaults to auto option"
      (sut/-main)
      (should-have-invoked :auto-run))

    (it "must be once or auto"
      (let [message "Assert failed: Unrecognized build command: foo. Must be 'once', 'auto', or 'spec'\n(#{\"once\" \"spec\" \"auto\"} command)"]
        (should-throw AssertionError message (sut/-main "foo"))))
    )
  )
