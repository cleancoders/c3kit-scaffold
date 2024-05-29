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
             :development {:specs      true
                           :output-dir "tmp"
                           :output-to  "tmp/out.cljs"}})

(describe "CLJS Compilation"
  (with-stubs)

  (redefs-around [println       ccc/noop
                  api/build     (stub :cljs/build)
                  sut/run-specs (stub :run-specs)])

  (before (.mkdir (io/file "tmp"))
          (reset! sut/build-config nil)
          (reset! sut/run-env {})
          (reset! sut/ns-prefix nil)
          (reset! sut/ignore-errors [])
          (reset! sut/ignore-consoles []))

  (after (io/delete-file "tmp/out.cljs")
         (io/delete-file "tmp/.specljs-timestamp"))

  (it "on-dev-compiled - no timestamp file"
    (sut/configure! config :development)
    (sut/on-dev-compiled)
    (should (.exists (io/file "tmp/out.cljs")))
    (should (.exists (io/file "tmp/.specljs-timestamp")))
    (should> (.lastModified (io/file "tmp/.specljs-timestamp")) 0)
    (should-have-invoked :run-specs {:with [:auto? true :timestamp 0]}))

  (it "on-dev-compiled - timestamp file exists"
    (sut/configure! config :development)
    (let [ts-file (io/file "tmp/.specljs-timestamp")]
      (spit ts-file "")
      (.setLastModified ts-file 1234)
      (sut/on-dev-compiled)
      (should-not= 1234 (.lastModified ts-file))
      (should-have-invoked :run-specs {:with [:auto? true :timestamp 1234]})))

  (context "main"
    (redefs-around [util/read-edn-resource (constantly config)
                    util/establish-path    (stub :establish-path)
                    io/delete-file         (stub :delete-file)
                    sut/auto-run           (stub :auto-run)])

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
