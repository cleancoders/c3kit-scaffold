# c3kit-scaffold Audit Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address every actionable point in `~/Desktop/c3kit-scaffold-audit.md` (excluding the Clojars token rotation, which the user is handling separately) so the library is ready for public consumption.

**Architecture:** Touch four areas in order — (1) code-quality bugs and idiom fixes in `cljs.clj`/`css.clj` plus their specs, (2) public-API docstrings, (3) CI workflow rewrite, (4) consumer-facing docs (README, CONTRIBUTING, CODE_OF_CONDUCT, CHANGES.md, issue/PR templates). Each code change goes RED → GREEN → REFACTOR with Speclj tests.

**Tech Stack:** Clojure 1.12 / ClojureScript 1.12, Speclj, Playwright, GitHub Actions, tools.build, Garden.

---

## Pre-flight

Before starting any task: run the existing test suite and confirm green.

```bash
clojure -M:test:spec
```

Expected: all tests pass. If any fail on `master`, stop and report — the plan assumes a green baseline.

---

## Section 1 — Code-quality fixes (audit §4–§11, polish bullets)

Each task in this section is small enough to land as its own commit. TDD applies: when a task changes runtime behavior, write the failing spec first, then make it pass.

---

### Task 1: Delete the duplicate `with-red` definition

**Audit reference:** §4.

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj:137-138`

- [ ] **Step 1: Confirm both definitions are byte-identical**

```bash
sed -n '106,107p;137,138p' src/c3kit/scaffold/cljs.clj
```

Expected: two identical pairs of lines defining `(defn- with-red [s] (str red s default-color))`.

- [ ] **Step 2: Delete the second definition (lines 137–138)**

Edit `src/c3kit/scaffold/cljs.clj` and remove these two lines:

```clojure
(defn- with-red [s]
  (str red s default-color))
```

The first definition at lines 106–107 stays.

- [ ] **Step 3: Run the full test suite**

Run: `clojure -M:test:spec`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj
git commit -m "remove duplicate with-red definition in cljs.clj"
```

---

### Task 2: Replace `if` with `when` for side-effecting branch in `print-error-summary`

**Audit reference:** §6.

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj:113-114`

- [ ] **Step 1: Run `cljs_spec.clj` and confirm it passes**

Run: `clojure -M:test:spec --focus "CLJS Compilation"`
Expected: passes.

- [ ] **Step 2: Replace the `if` with `when`**

In `src/c3kit/scaffold/cljs.clj`, change:

```clojure
(if exit-if-errors?
  (System/exit -1))
```

to:

```clojure
(when exit-if-errors?
  (System/exit -1))
```

- [ ] **Step 3: Run `clojure -M:test:spec`**

Expected: passes.

- [ ] **Step 4: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj
git commit -m "use when instead of if for side-effecting branch in print-error-summary"
```

---

### Task 3: Fix timestamp-file path inconsistency in `cljs/-main`

**Audit reference:** §7.

**Background:** `timestamp-file` returns `(io/file output-dir ".specljs-timestamp")`, but `-main` deletes/uses `".specljs-timestamp"` from CWD at lines 257 and 262–263. When `:output-dir` ≠ `.`, cleanup deletes the wrong file.

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj` (`-main`, lines 257 and 262–263)
- Modify: `spec/c3kit/scaffold/cljs_spec.clj` (the "main" context, two `should-have-invoked :delete-file` assertions and the "auto" path)

- [ ] **Step 1: Write the failing test for the once-mode delete path**

In `spec/c3kit/scaffold/cljs_spec.clj`, replace the assertion in `(it "runs once" ...)`:

```clojure
(should-have-invoked :delete-file {:with [".specljs-timestamp" true]})
```

with:

```clojure
(should-have-invoked :delete-file {:with [(io/file "tmp" ".specljs-timestamp") true]})
```

Apply the same change in `(it "runs automatically" ...)`.

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run: `clojure -M:test:spec --focus "main"`
Expected: failures on the new `:delete-file` assertions because the production code still passes the bare path.

- [ ] **Step 3: Update `cljs/-main` to use `(timestamp-file)`**

In `src/c3kit/scaffold/cljs.clj`, in `-main`:

Replace line 257:

```clojure
(io/delete-file ".specljs-timestamp" true)
```

with:

```clojure
(io/delete-file (timestamp-file) true)
```

Replace lines 261–263 (the `:else` branch's timestamp handling). The current code is:

```clojure
:else (let [timestamp (timestamp-file)]
        (println "watching namespaces with prefix:" @ns-prefix)
        (when (.exists timestamp) (.delete timestamp))
        ...)
```

That block already uses `timestamp-file` and is correct — leave it. The fix is only line 257.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `clojure -M:test:spec --focus "main"`
Expected: passes.

- [ ] **Step 5: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes.

- [ ] **Step 6: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj spec/c3kit/scaffold/cljs_spec.clj
git commit -m "use timestamp-file path in -main cleanup so :output-dir is honored"
```

---

### Task 4: Throw on missing `:ns-prefix` instead of using a sentinel default

**Audit reference:** §5.

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj:229` (`configure!`)
- Modify: `spec/c3kit/scaffold/cljs_spec.clj` (add a `configure!` context)

- [ ] **Step 1: Add `:ns-prefix` to the existing test `config` map first**

The existing `config` map at the top of `cljs_spec.clj` has no `:ns-prefix`. Once `configure!` throws on missing `:ns-prefix`, every test that calls `(sut/configure! config ...)` will start failing. Pre-emptively add it.

In `spec/c3kit/scaffold/cljs_spec.clj`, change the top-level `config` map from:

```clojure
(def config {:run-cmd     "command"
             :run-env     "environment"
             :env-keys    ["ENV" ".env"]
             ...})
```

to:

```clojure
(def config {:run-cmd     "command"
             :run-env     "environment"
             :env-keys    ["ENV" ".env"]
             :ns-prefix   "test.ns"
             ...})
```

- [ ] **Step 2: Write the failing test**

Add a new context inside the `"CLJS Compilation"` describe (place near the other config-driven contexts):

```clojure
(context "configure!"
  (it "throws when :ns-prefix is missing"
    (let [bad-config (-> config
                         (dissoc :ns-prefix))]
      (should-throw clojure.lang.ExceptionInfo #":ns-prefix"
        (sut/configure! bad-config :development))))

  (it "sets ns-prefix from config"
    (sut/configure! config :development)
    (should= "test.ns" @sut/ns-prefix)))
```

- [ ] **Step 3: Run the focused test and confirm RED**

Run: `clojure -M:test:spec --focus "configure!"`
Expected: the "throws when :ns-prefix is missing" assertion fails — current code falls back to the sentinel string. The "sets ns-prefix from config" assertion should pass already.

- [ ] **Step 4: Update `configure!` to throw**

In `src/c3kit/scaffold/cljs.clj`, replace:

```clojure
(reset! ns-prefix (:ns-prefix config "i.forgot.to.add.ns-prefix.to.cljs.edn"))
```

with:

```clojure
(when-not (:ns-prefix config)
  (throw (ex-info ":ns-prefix is required in config/cljs.edn" {:config config})))
(reset! ns-prefix (:ns-prefix config))
```

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run: `clojure -M:test:spec --focus "configure!"`
Expected: passes.

- [ ] **Step 6: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes. (Step 1's pre-emptive change to the test config keeps all other tests green.)

- [ ] **Step 7: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj spec/c3kit/scaffold/cljs_spec.clj
git commit -m "throw on missing :ns-prefix instead of papering over with sentinel"
```

---

### Task 5: Replace brittle assertion-message tests with regex matches

**Audit reference:** §11.

**Files:**
- Modify: `spec/c3kit/scaffold/cljs_spec.clj:159-161`
- Modify: `spec/c3kit/scaffold/css_spec.clj:90-92`

- [ ] **Step 1: Loosen the cljs assertion test**

Replace:

```clojure
(it "must be once or auto"
  (let [message "Assert failed: Unrecognized build command: foo. Must be 'once', 'auto', or 'spec'\n(#{\"once\" \"spec\" \"auto\"} command)"]
    (should-throw AssertionError message (sut/-main "foo"))))
```

with:

```clojure
(it "must be once, auto, or spec"
  (should-throw AssertionError #"Unrecognized build command: foo"
                (sut/-main "foo")))
```

- [ ] **Step 2: Loosen the css assertion test**

Replace:

```clojure
(it "must be once or auto"
  (let [message "Assert failed: Unrecognized build frequency: foo. Must be 'once' or 'auto'\n(#{\"once\" \"auto\"} once-or-auto)"]
    (should-throw AssertionError message (sut/-main "foo"))))
```

with:

```clojure
(it "must be once or auto"
  (should-throw AssertionError #"Unrecognized build frequency: foo"
                (sut/-main "foo")))
```

- [ ] **Step 3: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes.

- [ ] **Step 4: Commit**

```bash
git add spec/c3kit/scaffold/cljs_spec.clj spec/c3kit/scaffold/css_spec.clj
git commit -m "match assertion messages by regex prefix to survive set-iteration churn"
```

---

### Task 6: Fix `specs.html` charset and stale PhantomJS reference

**Audit reference:** §8.

**Files:**
- Modify: `src/c3kit/scaffold/specs.html` (lines 4 and ~42)

- [ ] **Step 1: Fix the meta tag**

Replace:

```html
<meta charset="iso-8859-1" content="text/html" http-equiv="Content-Type"/>
```

with:

```html
<meta charset="UTF-8"/>
```

- [ ] **Step 2: Replace PhantomJS reference**

Replace:

```html
<p style="margin: 1em; width: 400px;">
  Typically these specs are run using phantomjs on the command line.
  But you can run them here if you like.
  That is, assuming all the cljs has been compiled in development.
  <br/>
  Open up the browser console:
</p>
```

with:

```html
<p style="margin: 1em; width: 400px;">
  Typically these specs are run via Playwright on the command line.
  You can also run them here, assuming the ClojureScript has been compiled in development.
  <br/>
  Open the browser console and call:
</p>
```

- [ ] **Step 3: Run the full suite to confirm html-templating tests still pass**

Run: `clojure -M:test:spec --focus "build-spec-html"`
Expected: passes (these tests assert on script tags and config, not the meta tag — but verify).

- [ ] **Step 4: Commit**

```bash
git add src/c3kit/scaffold/specs.html
git commit -m "fix specs.html meta charset and update stale PhantomJS reference"
```

---

### Task 7: Sync `tools.build` version between `:test` and `:build` aliases

**Audit reference:** §10.

**Files:**
- Modify: `deps.edn:25` (`:build` alias)

- [ ] **Step 1: Bump `:build` alias to 0.10.12**

In `deps.edn`, change:

```clojure
:build {:extra-deps  {io.github.clojure/tools.build {:mvn/version "0.10.11"}
                      clj-commons/pomegranate       {:mvn/version "1.2.25"}}
```

to:

```clojure
:build {:extra-deps  {io.github.clojure/tools.build {:mvn/version "0.10.12"}
                      clj-commons/pomegranate       {:mvn/version "1.2.25"}}
```

- [ ] **Step 2: Verify the build still resolves**

Run: `clojure -T:build clean`
Expected: completes without errors.

- [ ] **Step 3: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes.

- [ ] **Step 4: Commit**

```bash
git add deps.edn
git commit -m "sync tools.build version to 0.10.12 across :build and :test aliases"
```

---

### Task 8: Log a single line when `auto-run` swallows a shutdown-time exception

**Audit reference:** polish bullet (`cljs.clj:208-212`).

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj:207-212`
- Modify: `spec/c3kit/scaffold/cljs_spec.clj` (`auto-run` context)

- [ ] **Step 1: Write the failing test**

In `spec/c3kit/scaffold/cljs_spec.clj`, add this `it` inside the existing `(context "auto-run" ...)`:

```clojure
(it "logs a one-line note when an exception is swallowed during shutdown"
  (let [logged (atom [])]
    (with-redefs [api/watch (fn [& _]
                              (vreset! sut/running false)
                              (throw (Exception. "boom")))
                  println    (fn [& args] (swap! logged conj (apply str args)))]
      (sut/auto-run {})
      (should (some #(re-find #"auto-run: ignoring exception during shutdown" %) @logged)))))
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `clojure -M:test:spec --focus "auto-run"`
Expected: failure — current code prints nothing during shutdown.

- [ ] **Step 3: Update `auto-run`**

Replace:

```clojure
(defn auto-run [build-options]
  (while @running
    (try
      (api/watch (Sources. build-options) build-options)
      (catch Exception e
        (when @running (.printStackTrace e))))))
```

with:

```clojure
(defn auto-run [build-options]
  (while @running
    (try
      (api/watch (Sources. build-options) build-options)
      (catch Exception e
        (if @running
          (.printStackTrace e)
          (println "auto-run: ignoring exception during shutdown:" (.getMessage e)))))))
```

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `clojure -M:test:spec --focus "auto-run"`
Expected: passes.

- [ ] **Step 5: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes.

- [ ] **Step 6: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj spec/c3kit/scaffold/cljs_spec.clj
git commit -m "log a note when auto-run drops an exception during shutdown"
```

---

### Task 9: Make `dev/build.clj` `tag` use `git status --porcelain`

**Audit reference:** polish bullet (`dev/build.clj:48`).

**Files:**
- Modify: `dev/build.clj:47-54`

- [ ] **Step 1: Update the cleanliness check**

In `dev/build.clj`, replace:

```clojure
(defn tag [_]
  (let [clean? (str/blank? (:out (shell/sh "git" "diff")))
        tags   (delay (->> (shell/sh "git" "tag") :out str/split-lines set))]
    (cond (not clean?) (do (println "ABORT: commit master before tagging") (System/exit 1))
          (contains? @tags version) (println "tag already exists")
          :else (do (println "pushing tag" version)
                    (shell/sh "git" "tag" version)
                    (shell/sh "git" "push" "--tags")))))
```

with:

```clojure
(defn tag [_]
  (let [clean? (str/blank? (:out (shell/sh "git" "status" "--porcelain")))
        tags   (delay (->> (shell/sh "git" "tag") :out str/split-lines set))]
    (cond (not clean?) (do (println "ABORT: commit master before tagging") (System/exit 1))
          (contains? @tags version) (println "tag already exists")
          :else (do (println "pushing tag" version)
                    (shell/sh "git" "tag" version)
                    (shell/sh "git" "push" "--tags")))))
```

- [ ] **Step 2: Sanity-check by running locally with a known-clean tree**

Run: `clojure -T:build tag` only when ready to actually tag (otherwise it will tag locally). For this task, verify by reading the diff — the function isn't unit-tested, and we don't want the side effect.

- [ ] **Step 3: Commit**

```bash
git add dev/build.clj
git commit -m "use git status --porcelain so build/tag catches staged-but-uncommitted files"
```

---

## Section 2 — Public-API docstrings (audit §9)

### Task 10: Add docstrings to the public API

**Files:**
- Modify: `src/c3kit/scaffold/cljs.clj` (`configure!`, `run-specs`, `auto-run`, `on-dev-compiled`, `shutdown!`, `install-shutdown-hook!`, `monitor-stdin!`)
- Modify: `src/c3kit/scaffold/css.clj` (`-main`, `auto-generate`, `on-dev-compiled`, `handle-error`, `shutdown!`, `install-shutdown-hook!`, `monitor-stdin!`)

- [ ] **Step 1: Add docstrings in `cljs.clj`**

Use this exact text for each function. Place the docstring immediately after the name in the existing `defn` form.

```clojure
(defn configure!
  "Reads the EDN config map and the chosen build-key (e.g. :development) and
  populates the runtime atoms (`build-config`, `run-env`, `ns-prefix`,
  `ignore-errors`, `ignore-consoles`). Throws via `resolve-build-config` if
  the build-key is missing, and throws if `:ns-prefix` is missing.

  Called once from `-main` before compilation."
  [config build-key]
  ...)

(defn run-specs
  "Launches Playwright, navigates to the generated specs.html, and runs the
  ClojureScript Speclj suite. With `:auto? true`, only specs affected by
  changes since `:timestamp` are run; otherwise, runs the full suite and
  exits the JVM with the Speclj status code. Always closes browser and
  Playwright resources on exit."
  [& {:keys [timestamp auto?]}]
  ...)

(defn auto-run
  "Runs `cljs.build.api/watch` in a loop while the `running` volatile is
  true. Exceptions thrown by `api/watch` are printed and the loop restarts;
  exceptions raised after a shutdown signal are logged briefly and dropped
  to keep shutdown clean."
  [build-options]
  ...)

(defn on-dev-compiled
  "Watch-fn callback for ClojureScript auto-compilation. Resets the error
  atom, touches the `.specljs-timestamp` file, touches the JS output file
  so the browser reloads it, then re-runs only the affected specs."
  []
  ...)

(defn shutdown!
  "Sets `running` to false and interrupts the given thread. Used by both
  the JVM shutdown hook and the stdin monitor to stop the auto-run loop."
  [main-thread]
  ...)

(defn install-shutdown-hook!
  "Registers a JVM shutdown hook that calls `shutdown!` on the current
  thread, so `kill`/Ctrl-C/parent-process-exit gracefully stops the
  auto-run loop."
  []
  ...)

(defn monitor-stdin!
  "Spawns a daemon thread that reads stdin until EOF, then calls
  `shutdown!`. Prevents orphaned subprocesses when the parent process
  closes stdin (e.g. agent harnesses that don't propagate signals)."
  []
  ...)
```

Also confirm `-main` already has a docstring (it does, lines 244–248). Leave it.

- [ ] **Step 2: Add docstrings in `css.clj`**

```clojure
(defn -main
  "Compile Garden CSS and optionally watch for changes.
  args:
    once  - compile once and exit
    auto  - compile, then watch the source-dir and recompile on change (default)"
  [& args]
  ...)

(defn auto-generate
  "Watches `:source-dir` for changes via tools.namespace, reloads any
  changed namespaces, and regenerates CSS. Loops while `running` is
  true."
  [config]
  ...)

(defn on-dev-compiled
  "If the config supplies an `:on-css-compiled` callback, invokes it with
  the resolved config map after each successful compilation."
  [config]
  ...)

(defn handle-error
  "Pretty-prints a Throwable raised during namespace reload so that watch
  mode keeps running."
  [error]
  ...)

(defn shutdown!
  "Sets `running` to false and interrupts the given thread to break out
  of the auto-generate loop."
  [main-thread]
  ...)

(defn install-shutdown-hook!
  "Registers a JVM shutdown hook so SIGTERM/SIGINT cleanly stops the
  auto-generate loop."
  []
  ...)

(defn monitor-stdin!
  "Spawns a daemon thread that reads stdin until EOF and then calls
  `shutdown!`. Stops orphaned CSS watchers when the parent process
  exits."
  []
  ...)
```

- [ ] **Step 3: Run the full suite**

Run: `clojure -M:test:spec`
Expected: passes (docstrings don't change behavior).

- [ ] **Step 4: Commit**

```bash
git add src/c3kit/scaffold/cljs.clj src/c3kit/scaffold/css.clj
git commit -m "document the public API with docstrings on cljs and css runners"
```

---

## Section 3 — CI improvements (audit §2)

### Task 11: Rewrite `.github/workflows/test.yml` to actually exercise CLJS + CSS on a JDK matrix

**Audit reference:** §2.

**Files:**
- Modify: `.github/workflows/test.yml`

- [ ] **Step 1: Replace the workflow with a complete rewrite**

Overwrite `.github/workflows/test.yml` with:

```yaml
name: Scaffold Build

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        java: [ '17', '21' ]
    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v5
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'

      - name: Install Clojure CLI
        uses: DeLaGuardo/setup-clojure@13.4
        with:
          cli: 'latest'

      - name: Cache Clojure dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.m2
            ~/.gitlibs
          key: ${{ runner.os }}-deps-${{ hashFiles('deps.edn') }}
          restore-keys: ${{ runner.os }}-deps-

      - name: Pre-fetch Clojure deps
        run: clojure -P -M:test

      - name: Install Playwright browsers
        run: clojure -M:test -m com.microsoft.playwright.CLI install --with-deps chromium

      - name: Run JVM tests
        run: clojure -M:test:spec

      - name: Run ClojureScript tests
        run: clojure -M:test:cljs once

      - name: Compile CSS
        run: clojure -M:test:css once
```

Notes for the implementer:

- `clojure -M:test -m com.microsoft.playwright.CLI install --with-deps chromium` invokes Playwright's bundled Java CLI to download the browser binaries and Linux package dependencies. No `npx`/Node required.
- The CSS step uses the existing `dev/config/css.edn`. If the CI environment lacks the sample sources at `spec/c3kit/scaffold/css/`, verify they exist (`ls spec/c3kit/scaffold/css/`) — they should, because the dev config points there.

- [ ] **Step 2: Trigger CI by pushing to a branch**

```bash
git checkout -b ci/jdk-matrix-cljs-css
git add .github/workflows/test.yml
git commit -m "test JVM, CLJS, and CSS pipelines on JDK 17 and 21 with Playwright"
git push -u origin ci/jdk-matrix-cljs-css
```

Expected: a fresh CI run shows green for both `java: 17` and `java: 21`. Iterate on the workflow until it does.

- [ ] **Step 3: Open a PR and merge once green**

```bash
gh pr create --fill --base master
```

Expected: PR open, CI green, merge to `master`.

---

## Section 4 — Consumer-facing documentation (audit §3 + OSS hygiene polish)

### Task 12: Rewrite `README.md` for consumers

**Audit reference:** §3.

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace the README**

Overwrite `README.md` with:

````markdown
# Scaffold

![Scaffold](https://github.com/cleancoders/c3kit/blob/master/img/scaffold_200.png?raw=true)

A library component of [c3kit — Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

[![Scaffold Build](https://github.com/cleancoders/c3kit-scaffold/actions/workflows/test.yml/badge.svg)](https://github.com/cleancoders/c3kit-scaffold/actions/workflows/test.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.cleancoders.c3kit/scaffold.svg)](https://clojars.org/com.cleancoders.c3kit/scaffold)

> _"Truth forever on the scaffold, Wrong forever on the throne, —
> Yet that scaffold sways the future"_ — James Russell Lowell

Scaffold is the build/test runner that the rest of the c3kit stack uses to develop ClojureScript single-page apps and Garden-based CSS:

- **`c3kit.scaffold.cljs`** drives a hot-reloading ClojureScript compiler tied to a Speclj test runner that runs your specs in a real browser via Playwright. In auto mode, only the specs affected by your latest edits re-run, so the feedback loop stays fast.
- **`c3kit.scaffold.css`** compiles a Garden var to a CSS file, optionally watching the source directory and re-compiling on change. Hooks let you run additional logic each time the CSS is generated.

See [`CHANGES.md`](./CHANGES.md) for release history.

## Installation

Add Scaffold to `deps.edn`:

```clojure
com.cleancoders.c3kit/scaffold {:mvn/version "2.3.3"}
```

Requires JDK 17 or newer.

## Usage

### ClojureScript build + spec runner

Add an alias to your `deps.edn`:

```clojure
:cljs {:main-opts ["-m" "c3kit.scaffold.cljs"]}
```

Create `config/cljs.edn` on the classpath. See [`dev/config/cljs.edn`](./dev/config/cljs.edn) for a complete example.

Run one of three subcommands:

```bash
clj -M:cljs            # auto: watch sources, recompile, re-run affected specs (default)
clj -M:cljs auto       # same as above, explicit
clj -M:cljs once       # compile and run specs once, then exit
clj -M:cljs spec       # just run specs (assumes a prior compilation)
```

The active environment is selected via the `c3.env` system property or the `C3_ENV` environment variable (override with `:env-keys`):

```bash
C3_ENV=production clj -M:cljs once
```

### CSS build (Garden)

Add an alias to your `deps.edn`:

```clojure
:css {:main-opts ["-m" "c3kit.scaffold.css"]}
```

Create `config/css.edn` on the classpath. See [`dev/config/css.edn`](./dev/config/css.edn).

Run:

```bash
clj -M:css         # auto: watch and recompile (default)
clj -M:css auto    # same as above, explicit
clj -M:css once    # compile once, exit
```

## Configuration reference

### `config/cljs.edn`

Top-level keys:

| Key | Required | Type | Purpose |
| --- | --- | --- | --- |
| `:ns-prefix` | yes | string | Only namespaces beginning with this prefix trigger recompiles and affect the spec-impact graph. |
| `:env-keys` | no | vector | Env-key search order. Default: `["c3.env" "C3_ENV"]`. The first key that resolves picks the build-key. |
| `:ignore-errors` | no | vector of regex strings | Page errors matching any pattern are silently dropped from the failure summary. |
| `:ignore-console` | no | vector of regex strings | Browser console messages matching any pattern are silently dropped from stdout. |

Each environment (`:development`, `:production`, ...) is a build-config map merged with [ClojureScript compiler options](https://clojurescript.org/reference/compiler-options) plus:

| Key | Type | Purpose |
| --- | --- | --- |
| `:specs` | map or false | Speclj `run_specs` options (e.g. `:tags`, `:reporters`). `false`/missing skips the spec runner. Defaults: `{:color true :reporters ["documentation"]}`. |
| `:watch-fn` | symbol | Fully-qualified function called after each successful auto-compile. Pass `c3kit.scaffold.cljs/on-dev-compiled` to enable affected-spec re-runs. |
| `:sources` | vector | Paths to compile (typically `["spec" "src"]`). |
| `:output-dir`, `:output-to` | string | Forwarded to the cljs compiler. `:output-dir` is also where Scaffold writes its `specs.html` and `.specljs-timestamp` files. |

### `config/css.edn`

| Key | Required | Type | Purpose |
| --- | --- | --- | --- |
| `:source-dir` | yes | string | Garden source directory watched in auto mode. |
| `:var` | yes | symbol | Fully-qualified var holding the root Garden data structure. |
| `:output-file` | yes | string | Where the compiled CSS is written. |
| `:flags` | no | map | Forwarded to `garden.core/css` (e.g. `{:pretty-print? true :vendors ["webkit" "moz" "o"]}`). |
| `:on-css-compiled` | no | symbol | Optional callback invoked after each successful compile. |

## Development / Contributing

Run the JVM tests:

```bash
clj -M:test:spec
clj -M:test:spec -a    # auto-runner
```

Compile and run the ClojureScript tests:

```bash
clj -M:test:cljs once
clj -M:test:cljs       # auto-runner
```

See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for branch conventions, test requirements, and how to file issues.

## Deployment

To deploy to Clojars you must be a member of the `com.cleancoders.c3kit` group.

1. Configure a deploy token at https://clojars.org/tokens.
2. Set `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` in your shell session for the deploy command only — do not persist them in `.env` or any file on disk.
3. Bump the `VERSION` file and update `CHANGES.md`.
4. `clj -T:build deploy`

## License

[MIT](./LICENSE) © Clean Coders.
````

(Replace the version snippet `"2.3.3"` with whatever `cat VERSION` yields at execution time.)

- [ ] **Step 2: Confirm renderable Markdown**

Run: `cat README.md | head -40`
Expected: renders cleanly. Open in an editor preview if available.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "rewrite README for consumers: install, usage, config reference, dev split"
```

---

### Task 13: Add `CONTRIBUTING.md`

**Audit reference:** polish bullet.

**Files:**
- Create: `CONTRIBUTING.md`

- [ ] **Step 1: Write the file**

Create `CONTRIBUTING.md`:

```markdown
# Contributing to c3kit-scaffold

Thanks for considering a contribution!

## Filing issues

Before opening an issue, search [existing issues](https://github.com/cleancoders/c3kit-scaffold/issues) to avoid duplicates. When filing a new bug, please include:

- The version of `c3kit-scaffold` you're using.
- Your `config/cljs.edn` or `config/css.edn` (redact anything sensitive).
- The exact command and the full output (or a stack trace).
- The Clojure CLI / JDK versions (`clojure --version`, `java -version`).

## Development setup

```bash
git clone https://github.com/cleancoders/c3kit-scaffold.git
cd c3kit-scaffold
clojure -M:test:spec      # JVM tests
clojure -M:test:cljs once # ClojureScript tests (requires Playwright)
```

## Submitting a pull request

1. Open an issue first if the change is non-trivial — it's faster to align on direction before code lands.
2. Branch from `master`.
3. Add or update tests for any behavior change. We use [Speclj](https://github.com/slagyr/speclj) and follow TDD (red → green → refactor).
4. Run the full test suite before pushing.
5. Keep commits small and well-described. Reference the issue number in the commit message when applicable.
6. Update `CHANGES.md` under an "Unreleased" heading describing the user-visible change.

## Code of Conduct

This project follows the [Clean Coders Code of Conduct](./CODE_OF_CONDUCT.md). By participating you agree to abide by it.
```

- [ ] **Step 2: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "add CONTRIBUTING.md with setup, PR, and issue guidelines"
```

---

### Task 14: Add `CODE_OF_CONDUCT.md`

**Audit reference:** polish bullet.

**Files:**
- Create: `CODE_OF_CONDUCT.md`

- [ ] **Step 1: Add a Contributor Covenant 2.1 file**

Use the standard Contributor Covenant text. Create `CODE_OF_CONDUCT.md` with the contents of [Contributor Covenant 2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/code_of_conduct.md), substituting the contact line:

```
Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported to the project maintainers at <fill in: a real reporting address,
e.g. info@cleancoders.com>. All complaints will be reviewed and investigated
promptly and fairly.
```

If Clean Coders has its own Code of Conduct, prefer that and replace this file's contents accordingly.

- [ ] **Step 2: Commit**

```bash
git add CODE_OF_CONDUCT.md
git commit -m "add Code of Conduct (Contributor Covenant 2.1)"
```

---

### Task 15: Add issue and PR templates

**Audit reference:** polish bullet.

**Files:**
- Create: `.github/ISSUE_TEMPLATE/bug_report.md`
- Create: `.github/ISSUE_TEMPLATE/feature_request.md`
- Create: `.github/pull_request_template.md`

- [ ] **Step 1: Bug report template**

Create `.github/ISSUE_TEMPLATE/bug_report.md`:

```markdown
---
name: Bug report
about: Report a defect in c3kit-scaffold
labels: bug
---

**What happened?**
A clear, concise description of the bug.

**What did you expect?**

**Reproduction**
1. ...
2. ...

**Environment**
- c3kit-scaffold version:
- Clojure CLI: `clojure --version`
- JDK: `java -version`
- OS:

**Configuration**
Relevant excerpts from `config/cljs.edn` or `config/css.edn` (redact secrets).

**Logs / stack trace**
```

- [ ] **Step 2: Feature request template**

Create `.github/ISSUE_TEMPLATE/feature_request.md`:

```markdown
---
name: Feature request
about: Suggest a new capability
labels: enhancement
---

**Problem you're trying to solve**

**Proposed solution**

**Alternatives considered**

**Additional context**
```

- [ ] **Step 3: Pull-request template**

Create `.github/pull_request_template.md`:

```markdown
## Summary

<!-- One or two sentences. Why does this PR exist? -->

## Changes

- [ ] ...

## Testing

- [ ] `clojure -M:test:spec`
- [ ] `clojure -M:test:cljs once`

## Checklist

- [ ] Updated `CHANGES.md`
- [ ] Added or updated tests for any behavior change
- [ ] Linked the issue this PR addresses (if any)
```

- [ ] **Step 4: Commit**

```bash
git add .github/ISSUE_TEMPLATE .github/pull_request_template.md
git commit -m "add bug, feature, and PR templates"
```

---

### Task 16: Reformat `CHANGES.md` to Keep-a-Changelog style

**Audit reference:** polish bullet (`### 2.3.0` has a leading space, no Added/Changed/Fixed sections).

**Files:**
- Modify: `CHANGES.md`

- [ ] **Step 1: Rewrite the file**

Overwrite `CHANGES.md` with:

```markdown
# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and GitHub issue + PR templates.
- Public-API docstrings on `cljs` and `css` runners.

### Changed

- README rewritten for consumers: install snippet, usage, configuration reference, and a separated Development section.
- `:ns-prefix` is now required in `config/cljs.edn`; missing it throws instead of using a sentinel.
- CI now exercises CLJS specs and CSS compilation across JDK 17 and 21 via a matrix; Clojure CLI installs via `DeLaGuardo/setup-clojure`.
- `auto-run` logs a one-line note when an exception is dropped during shutdown.
- `dev/build.clj`'s `tag` task uses `git status --porcelain` to detect uncommitted changes.

### Fixed

- Removed duplicate `with-red` definition in `cljs.clj`.
- Replaced `if` with `when` for the side-effecting branch in `print-error-summary`.
- `cljs/-main` now deletes the timestamp file inside `:output-dir`, not from CWD.
- `specs.html` uses `<meta charset="UTF-8">` and references Playwright instead of PhantomJS.
- Brittle assertion-message tests in `cljs_spec.clj` and `css_spec.clj` now match by regex prefix.
- `:build` and `:test` aliases agree on `tools.build 0.10.12`.

## [2.3.3] - 2026-04-XX

### Changed

- Monitor stdin in both CLJS and CSS for auto shutdown to prevent orphaned/stale processes.
- Bumped non-breaking dependency versions:
  - c3kit-apron 2.4.2 → 2.5.0
  - Playwright 1.56.0 → 1.58.0
  - Clojure 1.12.3 → 1.12.4
  - clojure/tools.namespace 1.5.0 → 1.5.1
  - tools.build 0.10.11 → 0.10.12 (test alias)
  - ClojureScript 1.12.42 → 1.12.134

## [2.3.2]

### Added

- Shutdown hook for the CSS runner to stop orphaned processes.

## [2.3.1]

### Fixed

- Orphaned processes when run as a subprocess (e.g. spawned by Claude Code with no signal propagation):
  - Kill Playwright instance in `run-specs`, not just the browser.
  - Add a JVM shutdown hook to `-main`.

## [2.3.0]

### Changed

- Replaced `lambdaisland/garden` with `io.github.brandoncorrea/garden`, which fixes CSS compression around `calc()`.

## [2.2.0]

### Changed

- Upgraded dependencies.
- Replaced `noprompt/garden` with `lambdaisland/garden`.
- Bumped apron to 2.2.0.

## [2.0.5]

### Changed

- Bumped apron.

## [2.0.3]

### Added

- `:specs` config now accepts a map of options forwarded to `speclj.run.standard.run_specs` (defaults: `{:color true :reporters ["documentation"]}`). Providing options implies execution of specs.

### Changed

- Upgraded dependencies.

### Fixed

- CLJS spec autorun occasionally skipped re-runs when a file was saved during a transpilation phase.
```

(Fill in the actual 2.3.3 release date when known.)

- [ ] **Step 2: Commit**

```bash
git add CHANGES.md
git commit -m "rewrite CHANGES.md in Keep-a-Changelog format with Unreleased section"
```

---

## Final verification

- [ ] **Step 1: Full local CI dry-run**

Run, in order:

```bash
clojure -M:test:spec
clojure -M:test:cljs once
clojure -M:test:css once
```

Expected: all pass.

- [ ] **Step 2: Sanity-check the README in a renderer**

Push to a branch, look at the rendered README on GitHub. Confirm:

- Clojars badge resolves and shows the current version.
- Code blocks render with correct language.
- All internal links (`./CHANGES.md`, `./CONTRIBUTING.md`, `./CODE_OF_CONDUCT.md`, `./LICENSE`, `./dev/config/*.edn`) resolve.

- [ ] **Step 3: Open a single rollup PR for the audit**

```bash
gh pr create --title "address open-source readiness audit" --body "Implements the c3kit-scaffold audit punch list (excluding Clojars token rotation, handled separately by the maintainer)." --base master
```

Expected: green CI matrix, ready to merge.

---

## Items intentionally not addressed

- **Clojars token rotation** — out of scope per maintainer instruction; secret was gitignored, never committed.
- **`clj-kondo`** — out of scope per maintainer instruction. Revisit later if a contributor wants a static-analysis floor.
- **`cloverage`** — listed as optional in the audit. Skipped to keep the PR focused; revisit if a contributor asks for coverage signal.
