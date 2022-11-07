# Scaffold

![Scaffold](https://github.com/cleancoders/c3kit/blob/master/img/scaffold_200.png?raw=true)

A library component of [c3kit - Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

_"Truth forever on the scaffold, Wrong forever on the throne,—
Yet that scaffold sways the future"_ - James Russell Lowell

Use Scaffold to build your cljs and css (garden).

* __cljs.clj__ : task to compile clojurescript
* __css.clj__ : task to compile garden into css

# Development

    # Run the JVM tests
    clj -M:test:spec
    clj -M:test:spec -a         # auto runner

    # Compile and Run JS tests
    clj -M:test:cljs once
    clj -M:test:cljs            # auto runner
