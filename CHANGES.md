### 2.3.1
 * Fixes bug that caused orphaned processes when running as a sub-process, e.g. when run with Claude Code and the Claude Code session ends without killing the processes it spawned.
   * Kill Playwright instance in run-specs, not just browser
   * Add shutdown hook to main
   
 ### 2.3.0
 * Replaces lambdaisland/garden with io.github.brandoncorrea/garden
   * Fixes CSS compressions around `calc()`

### 2.2.0
 * Upgrades deps
 * Replaces noprompt/garden with lambdaisland/garden
 * Apron 2.2.0

### 2.0.5
 * bump apron

### 2.0.3
 * Upgrades deps
 * Fixes an issue in cljs where tests would sometimes not rerun if a file was saved during a transpilation phase
 * Updates cljs config option `:specs` to accept a map of options to be passed into `speclj.run.standard.run_specs`
   * defaults `{:color true :reporters ["documentation"]}`
   * providing options implies execution of specs
