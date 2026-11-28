# Alloy

Exploration whether the rules of the OSGi module layer can be formalized in Alloy and checked against the Cucumber scenarios of the [test-suite](../test-suite).

## What is Alloy

[Alloy](https://alloytools.org) is a lightweight formal specification language from MIT based on relational logic.

- A model declares *signatures* (types such as `Bundle`, `Export`) with *fields* (relations such as `exports` or `wire`).
- *Facts* and *predicates* constrain which instances are valid, for example "a bundle sees every package from one provider only".
- `run` searches an instance that satisfies a predicate, `check` searches a counterexample to an assertion.
- The analyzer translates the model into a SAT problem for a bounded scope, so "no counterexample" means "none up to that size".
- Alloy 6 needs Java 17 or later, the module is compiled for Java 17.

The language reference is at <https://alloytools.org/documentation.html>.

## How it works

The module is a Cucumber binding like [felix-resolver](../felix-resolver), but the "resolver" is the Alloy model in `src/test/resources` (`resolver.als` and `module_layer.als`).
It uses the feature files of the test-suite and its own steps in `org.eclipse.osgi.technology.resolver.alloy`.

- Steps that describe bundles record them in `AlloyWorld` and build the resources with the `BundleBuilder` of the test-suite.
- Which capabilities satisfy a requirement (version ranges, attributes, filters) is not part of the model: like a resolver, the model gets the candidates from `DefaultResolveContext.findProviders` and only decides which of them are wired.
- `I resolve ...` records the mandatory bundles, `Then` steps only record the expected outcome and assertions as Alloy formulas.
- An `@After` hook (`ResolveSteps.solve`) builds the model of the scenario and solves it with Alloy:
  a successful scenario needs a valid resolution that satisfies all assertions, a failing one must have none (a uses failure also needs one without the uses constraint).
- A step the model can not express yet calls `AlloyWorld.unsupported`, which is an `assumeFalse`, so the scenario is reported as skipped with the reason.

The result is a normal test run: passed means the model agrees with the scenario, skipped means not expressed yet, failed means model and scenario disagree.

## How to run

```
mvn -pl alloy -am verify -Dtest=RunCucumberTest -Dsurefire.failIfNoSpecifiedTests=false
```

The models are passed to Alloy as strings, nothing is written to disk.
To inspect them, set the system property `alloy.dump` to a directory (relative paths are relative to the module), each scenario is then written as `<feature>-<line>.als` next to a copy of the model files:

```
mvn -pl alloy -am verify -Dtest=RunCucumberTest -Dsurefire.failIfNoSpecifiedTests=false -Dalloy.dump=target/alloy
```

The dumped files can be opened in the Alloy GUI (`java -jar org.alloytools.alloy.dist-6.2.0.jar`) to browse instances, for example after changing a `run` command to look at a solution.

## How to extend

- New rules go into the fitting model file and get the next rule number in a comment.
- To support a step, replace its `world.unsupported(...)` in `BundleSteps` or `ResolveSteps` by code that records it, and extend `AlloyModelWriter` if the model needs new atoms.
