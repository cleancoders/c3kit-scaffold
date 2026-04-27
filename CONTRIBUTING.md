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

This project follows the [Contributor Covenant Code of Conduct](./CODE_OF_CONDUCT.md). By participating you agree to abide by it.
