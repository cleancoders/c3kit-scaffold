# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.3.4] - 2026-04-28

### Added

- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and GitHub issue + PR templates.
- Public-API docstrings on `cljs` and `css` runners.

### Changed

- README rewritten for consumers: install snippet, usage, configuration reference, and a separated Development section.
- `:ns-prefix` is now required in `config/cljs.edn`; missing it throws instead of using a sentinel.
- CI now exercises CLJS specs and CSS compilation on JDK 21 (the floor required by the bundled `closure-compiler`); Clojure CLI installs via `DeLaGuardo/setup-clojure`.
- `auto-run` logs a one-line note when an exception is dropped during shutdown.
- `dev/build.clj`'s `tag` task uses `git status --porcelain` to detect uncommitted changes.

### Fixed

- Removed duplicate `with-red` definition in `cljs.clj`.
- Replaced `if` with `when` for the side-effecting branch in `print-error-summary`.
- `cljs/-main` now deletes the timestamp file inside `:output-dir`, not from CWD.
- `specs.html` uses `<meta charset="UTF-8">` and references Playwright instead of PhantomJS.
- Brittle assertion-message tests in `cljs_spec.clj` and `css_spec.clj` now match by regex prefix.
- `:build` and `:test` aliases agree on `tools.build 0.10.12`.

## [2.3.3] - 2026-03-12

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
