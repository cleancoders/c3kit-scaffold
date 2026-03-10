(ns c3kit.scaffold.css-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.util :as util]
            [c3kit.scaffold.css :as sut]
            [clojure.tools.namespace.dir :as track]
            [clojure.tools.namespace.reload :as reload]
            [speclj.core :refer :all])
  (:import (java.lang AssertionError)))

(def config {:output-file "stylesheet.css"})

(describe "CSS Compilation"
  (with-stubs)
  (redefs-around [println ccc/noop])

  (context "shutdown!"
    (before (vreset! sut/running true))
    (after (vreset! sut/running true)
           (Thread/interrupted))

    (it "sets running to false"
      (sut/shutdown! (Thread/currentThread))
      (should= false @sut/running))

    (it "interrupts the given thread"
      (Thread/interrupted)
      (sut/shutdown! (Thread/currentThread))
      (should (.isInterrupted (Thread/currentThread))))
    )

  (context "monitor-stdin!"
    (before (vreset! sut/running true))
    (after (vreset! sut/running true)
           (Thread/interrupted))

    (it "calls shutdown when stdin closes"
      (let [original-in System/in
            empty-stream (java.io.ByteArrayInputStream. (byte-array 0))]
        (try
          (System/setIn empty-stream)
          (with-redefs [sut/shutdown! (stub :shutdown)]
            (sut/monitor-stdin!)
            (Thread/sleep 100)
            (should-have-invoked :shutdown))
          (finally (System/setIn original-in)))))
    )

  (context "auto-generate"
    (redefs-around [track/scan (stub :scan {:return {}})
                    reload/track-reload (stub :reload {:return {}})
                    sut/generate (stub :generate)])
    (before (vreset! sut/running true))
    (after (vreset! sut/running true))

    (it "exits immediately when not running"
      (vreset! sut/running false)
      (sut/auto-generate config)
      (should-not-have-invoked :scan))

    (it "stops looping when running becomes false"
      (with-redefs [track/scan (stub :scan {:invoke (fn [& _] (vreset! sut/running false) {})})]
        (sut/auto-generate config)
        (should-have-invoked :scan {:times 1})))
    )

  (context "main"
    (redefs-around [sut/generate              (stub :generate)
                    sut/auto-generate         (stub :auto-generate)
                    sut/install-shutdown-hook! (stub :install-hook)
                    sut/monitor-stdin!        (stub :monitor-stdin)
                    util/read-edn-resource    (constantly config)
                    util/establish-path       (stub :establish-path)])

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

    (it "installs shutdown hook in auto mode"
      (sut/-main "auto")
      (should-have-invoked :install-hook))

    (it "monitors stdin in auto mode"
      (sut/-main "auto")
      (should-have-invoked :monitor-stdin))
    )

  )
