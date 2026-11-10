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

## How to extend

Anything that is independent of a particular choice of a resolver and speeds up the lookup of capabilities belongs into this module.
Keep it free of dependencies on resolver implementations, key by identity instead of `equals`, return iterated results in a stable order, and add a JUnit test showing the behavior.
