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
             :development {:specs     true
                           :output-to "out.cljs"}})

(describe "CLJS Compilation"
  (with-stubs)

  (redefs-around [println        ccc/noop
                  io/delete-file (stub :delete-file)
                  api/build      (stub :cljs/build)])

  (context "main"
    (redefs-around [util/read-edn-resource (constantly config)
                    util/establish-path    (stub :establish-path)
                    sut/run-specs          (stub :run-specs)
                    sut/auto-run           (stub :auto-run)])

    (it "runs once"
      (sut/-main "once")
      (should= "environment" @sut/run-env)
      (should= (:development config) @sut/build-config)
      (should-have-invoked :establish-path {:with ["out.cljs"]})
      (should-have-invoked :delete-file {:with [".specljs-timestamp" true]})
      (should-have-invoked :cljs/build)
      (should-have-invoked :run-specs {:with [false]})
      (should-not-have-invoked :auto-run))

    (it "runs automatically"
      (sut/-main "auto")
      (should= "environment" @sut/run-env)
      (should= (:development config) @sut/build-config)
      (should-have-invoked :establish-path {:with ["out.cljs"]})
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
