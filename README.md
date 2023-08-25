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
2. Add the following to ~/.m2/settings.xml

```xml
<servers>
    <server>
        <id>clojars</id>
        <username>[clojars username]</username>
        <password>[deploy token]</password>
    </server>
</servers>
```

3. If dependencies were changed, run `clj -Spom` to regenerate the `pom.xml` file in the root dir of the project.
4. Update the `version` in `pom.xml` and ensure that the `groupId` and `artifactId` are set for the project (e.g. `com.cleancoders.c3kit` and `scaffold`, respectively)
5. Build the jar using `clj -T:build jar`
6. Deploy to maven `mvn deploy`

