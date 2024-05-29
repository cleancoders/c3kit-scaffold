(ns c3kit.scaffold.css-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.util :as util]
            [c3kit.scaffold.css :as sut]
            [speclj.core :refer :all])
  (:import (java.lang AssertionError)))

(def config {:output-file "stylesheet.css"})

(describe "CSS Compilation"
  (with-stubs)
  (redefs-around [println ccc/noop])

  (context "main"
    (redefs-around [sut/generate           (stub :generate)
                    sut/auto-generate      (stub :auto-generate)
                    util/read-edn-resource (constantly config)
                    util/establish-path    (stub :establish-path)])

    (it "compiles css once"
      (sut/-main "once")
      (should-have-invoked :establish-path {:with ["stylesheet.css"]})
      (should-have-invoked :generate {:with [config]})
      (should-not-have-invoked :auto-generate))

    (it "compiles css automatically"
      (sut/-main "auto")
      (should-have-invoked :establish-path {:with ["stylesheet.css"]})
      (should-have-invoked :auto-generate {:with [config]})
      (should-not-have-invoked :generate))

    (it "defaults to auto option"
      (sut/-main)
      (should-have-invoked :auto-generate {:with [config]}))

    (it "must be once or auto"
      (let [message "Assert failed: Unrecognized build frequency: foo. Must be 'once' or 'auto'\n(#{\"once\" \"auto\"} once-or-auto)"]
        (should-throw AssertionError message (sut/-main "foo"))))
    )

  )
