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
