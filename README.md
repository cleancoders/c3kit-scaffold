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

# Deployment

In order to deploy to c3kit you must be a member of the Clojars group `com.cleancoders.c3kit`.

1. Go to https://clojars.org/tokens and configure a token with the appropriate scope
2. Set the following environment variables

```
CLOJARS_USERNAME=<your username>
CLOJARS_PASSWORD=<your deploy key>
```

3. Update VERSION file
4. `clj -T:build deploy`

