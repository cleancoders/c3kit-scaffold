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
