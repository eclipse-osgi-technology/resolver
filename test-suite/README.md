# Resolver Test Suite

Cucumber scenarios that exercise a resolver through the `org.osgi.service.resolver` API and the glue code to run them against any implementation.

## Purpose

The scenarios describe the behavior of a resolver in the language of bundles, exports, imports and wires.
They are written against the OSGi Resolver Service Specification and the module layer of the OSGi Core specification, each feature names the section it is based on.
Any resolver that provides a small harness can be run against the scenarios, so implementations can be validated against the specification and compared with each other.

## Architecture

The module is a library, it does not execute any test itself.
The scenarios are run by the harness of each resolver.

- `src/main/resources/features` - the feature files, one per topic of the specification
- `ParameterTypes`, `BundleSteps` and `ResolveSteps` - the Cucumber glue code that maps the sentences of a scenario (steps) to Java methods, split into describing bundles and resolving/asserting
- `TestWorld` - the state of one scenario: the available bundles, from which the resolve is started
- `BundleBuilder` - builds the resource of a bundle with bnd, so capabilities and requirements are derived as for real bundles
- `DefaultResolveContext` - a plain resolve context in which all bundles are available, providers are found by namespace and filter (using the `index` module) and ordered by highest version first
- `ResolverEngine` - the service provider interface that connects a resolver to the scenarios

After every scenario with a successful resolve the result is checked with the `verifier` module, so it has to be a well formed wiring with consistent class spaces whatever the scenario asserts.

Failures are asserted semantically, for example that a bundle has an unsatisfied import of a package.
The specification does not say how a resolver reports a failure, so each `ResolverEngine` checks what its resolver can tell about it.

## Scenario language

```gherkin
Given there is bundle "exporter"
And it exports package "foo" in version "1.2.0"
And there is bundle "importer"
And it imports package "foo" in version range "[1,2)"
When I resolve bundle "importer"
Then the resolution succeeds
And bundle "importer" is wired to bundle "exporter" for package "foo"
```

- `there is bundle "name"` starts a bundle, a version can be given with `version 1.0.0`, all following steps that start with `it` describe this bundle.
- Bundles are never added to a set explicitly, starting the next bundle or resolving completes the current one.
- All bundles are available to the resolver, the ones named in the `I resolve` step are mandatory.
- Every step describes one complete clause of the manifest.
- Attributes and directives that have no wording of their own are added with a trailing `with "<attributes>"` in the syntax of a manifest clause, e.g. `it exports package "foo" with "color=red;mandatory:=color"`.

## How to run the suite against a resolver

Create a module (or any project) that has the resolver and this module on its test classpath and add:

1. An implementation of `ResolverEngine` that calls the resolver, registered in `META-INF/services/org.eclipse.osgi.technology.resolver.testsuite.ResolverEngine`.
2. A JUnit platform suite that selects the package `features` and uses `org.eclipse.osgi.technology.resolver` as glue, see `RunCucumberTest` of the [felix-resolver](../felix-resolver) module.

## How to extend

- Add scenarios to the feature file of the topic, or create a new feature file named after the topic.
  Start the file with comments naming the specification section with its public URL.
- Keep a scenario small and focused on one rule and write it in the domain language, a reader should understand it without reading the glue code.
- Add a step of the same style when the language lacks one instead of writing scenario specific steps.
  Concepts of the specification get their own wording, `with "<attributes>"` is reserved for attributes and directives that have none.
- Test only observable state: the wiring or the failure, never the internals of a resolver.
- Gherkin files have one sentence per line.
- A new kind of expected failure needs a new assertion method in `ResolverEngine`.
- If a scenario fails with a reference resolver, first check whether it tests the resolve context or the framework rather than the resolver, or whether the specification leaves the outcome open.

## Theory and references

The scenarios are written as [behavior-driven development](https://en.wikipedia.org/wiki/Behavior-driven_development) (BDD) specifications with [Cucumber](https://cucumber.io/docs/bdd/) instead of classic unit tests.
A resolver is a black box with a precise contract, bundles go in and a wiring or a failure comes out, so the interesting statements are about observable behavior rather than internals.
Written in the language of bundles, exports and wires, a scenario can be read, discussed and reviewed against the specification by people who do not know any resolver code, and it is the same text for every implementation.
Classic unit tests would couple the tests to the structure of one implementation and could not be shared between resolvers.

The feature files are independent of the technology that runs them.
The glue code provided here is only one binding, written for Java.
If it does not fit, for example because a resolver needs a different way of being driven, one can write an own binding for the same feature files.
Cucumber is available for [many languages](https://cucumber.io/docs/installation/), so in theory even a resolver written in a language other than Java can be tested with the same scenarios.

- [Gherkin reference](https://cucumber.io/docs/gherkin/reference/), the syntax of the feature files
- [Cucumber JVM](https://github.com/cucumber/cucumber-jvm), the Java implementation with the JUnit platform engine used here
- [Specification by Example](https://en.wikipedia.org/wiki/Specification_by_example), the practice of describing requirements with concrete examples
- [OSGi Core Module Layer](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html) and [Resolver Service Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html), the specifications the scenarios are based on
- [bnd](https://bnd.bndtools.org/), used to build the resources of the bundles
