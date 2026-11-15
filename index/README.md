# Resolver Index

Lookup structures over the resources and capabilities of a resolve, usable from any resolver or `ResolveContext`.

## Purpose

Finding the providers of a requirement by scanning every capability gets slow with the size of the repository, and capability `equals` methods of model implementations are comparatively expensive.
The index answers the question once per namespace with plain lists and an index by the primary attribute, and never compares capabilities by value.

The module depends only on the OSGi resource API and uses the OSGi framework `Filter` (a `provided` dependency, `osgi.core`).
It is compiled for Java 8, so it can be used from any resolver or tool regardless of its Java level.

## Architecture

`CapabilityIndex`
: Holds the capabilities of one namespace of a set of resources.
They are grouped by the value of the attribute that is named like the namespace (the primary attribute, e.g. the package name for `osgi.wiring.package`).
A capability with several values (a collection or an array) is found under each of them, one with a value that is not a string is a candidate for every requirement.
Capabilities are kept and compared by identity, and results are returned in the order of the resources, so lookups are deterministic.

```java
CapabilityIndex index = new CapabilityIndex("osgi.wiring.package", resources);
List<Capability> providers = index.findProviders(requirement);
```

`findProviders` preselects by the value that the filter of the requirement demands of the primary attribute, then lets the filter decide.
For the `osgi.wiring.*` namespaces a capability with a `mandatory` directive is only returned if the filter mentions all attributes it names.
Parsed filters are cached per index.
The result is a new list in the order of the resources, ordering by version or by any other criterion is up to the caller.
An index is built from a fixed set of resources and must be rebuilt when the resources change.

`UsesIndex`
: Static facts about the package imports, exports and `uses` directives of a resolve, built once from everything a resolver can choose from.
It knows which package names are _contested_, that means more than one capability exists for them, because only then a `uses` constraint on the package can be violated.
It also knows which package names _reach_ a contested one through the `uses` directives of everything that can provide them.
A chain of `uses` that never reaches a contested package is consistent whatever is chosen, so a check can skip it (`isContested`, `reachesContested`).
The imports of a resource by package name and the importers of a provider are answered by a lookup as well.

`SubstitutionIndex`
: The exports that their own bundle imports as well (`Substitution`), because the export is discarded if the import is wired to another provider.
For each of them it knows the requirements of other resources that could be wired to the export (the demand), and whether a resource that is certain to be resolved can not do without it (pinned).

`CandidateSource`
: Tells `UsesIndex` and `SubstitutionIndex` which capabilities a resolver may choose from for a requirement, so they work with any resolver.

```java
UsesIndex index = new UsesIndex(resources, requirement -> candidatesOf(requirement), resource -> true);
if (index.reachesContested("org.example.api")) {
    // the class space through this package has to be checked
}
```

All of them are built once from the resources and must be rebuilt when these change.
Resources, requirements and capabilities are keyed by identity, and everything that is returned for iteration keeps the order in which it was found.

## How to extend

Anything that is independent of a particular choice of a resolver and answers a question about the static structure of a resolve belongs into this module.
Keep it free of dependencies on resolver implementations, key by identity instead of `equals`, return iterated results in a stable order, and cover it with a test, either a JUnit test of the structure or scenarios of the test suite.
