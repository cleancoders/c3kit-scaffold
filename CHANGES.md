### 2.0.3
 * Upgrades deps
 * Fixes an issue in cljs where tests would sometimes not rerun if a file was saved during a transpilation phase
 * Updates cljs config option `:specs` to accept a map of options to be passed into `speclj.run.standard.run_specs`
   * defaults `{:color true :reporters ["documentation"]}`
   * providing options implies execution of specs
