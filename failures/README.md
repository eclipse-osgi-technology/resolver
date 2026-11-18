# Resolver Failures

Specific `ResolutionException` subclasses that tell a caller why a resolve operation failed.
The Resolver Service specification only offers the plain `ResolutionException` with a message and the list of unresolved requirements.
To react to a failure (show it, repair the input, assert it in a test) the message would have to be parsed.
This module gives the expected reasons a type each, with the details as accessors.

## Purpose

A resolver throws a plain `ResolutionException` only for situations it does not foresee.
Every failure it expects is one of the subclasses below, so all of them can be caught as `ResolutionException` and a caller that knows more can ask for more.
The types do not depend on any resolver implementation, so every resolver, test suite or tool can throw and handle them.

The module has no dependencies besides the OSGi core APIs and is compiled for Java 8, so it can be used from any resolver or tool regardless of its Java level.

## Failure types

`UnsatisfiedRequirementException`
: A mandatory requirement has no candidate capability at all.
  This is the root cause, not a resource that fails only because it depends on another failing resource.
  `getRequirement()` is the requirement, its resource is the one that can not be resolved, and it is the only entry of `getUnresolvedRequirements()`.

`ExportSubstitutedException`
: A subtype of `UnsatisfiedRequirementException`.
  The requirement does have a candidate, but it is an export that is discarded because its exporter imports the same package and has to use the export of another provider (OSGi Core, "Importing and Exporting the Same Package").
  `getExport()` is the discarded export and `getSubstitute()` the capability the exporter is wired to instead.

`UsesConstraintViolationException`
: No consistent class space exists, a resource would see a package from different providers because of the `uses` directive of an exported package.
  `getPackageName()`, `getResource()` and `getConflictingCapabilities()` tell which package, who sees it and which exports would have to be seen at once.

`FragmentConflictException`
: A subtype of `UsesConstraintViolationException`.
  The violation is caused by a fragment that was asked for explicitly, so it can not be left out.
  `getFragment()` is the fragment to blame and `getHost()` the host it makes inconsistent.

```java
try {
    resolver.resolve(context);
} catch (UnsatisfiedRequirementException e) {
    report(e.getRequirement().getResource(), e.getRequirement());
} catch (UsesConstraintViolationException e) {
    report(e.getPackageName(), e.getConflictingCapabilities());
} catch (ResolutionException e) {
    // everything else, only the message is known
}
```

## How to extend

A new failure type is a subclass of `ResolutionException` (or of one of the types above if every handler of the parent can also handle it) that carries its details in transient fields and exposes them through accessors.
It states in its Javadoc what the root cause is, and is added to this README.
The types are kept small and free of any resolver specific knowledge.

## References

- [OSGi Resolver Service Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html)
- [Provide semantic subclasses of `ResolutionException`](https://github.com/osgi/org.osgi.service.resolver/issues/49), the proposal for the specification these types follow

The types are meant as a working example for that proposal.
Using them in resolvers and in the test suite shows whether the kinds and accessors are sufficient and where they need to change, so the specification effort can build on practical experience.
They are not part of the OSGi specification and can change until the proposal is settled.
