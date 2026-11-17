# Resolver Class Space Rules

The rules that decide if the providers chosen for a resource give it a consistent class space.

## Purpose

Satisfying every requirement of a bundle is not enough: the bundle must also see every package from one provider only.
Two rules of the OSGi Core specification express that, and both can be answered from a candidate graph and a static index without searching for anything.
A resolver, a search strategy or a verification tool can ask the rules whether a selection is acceptable and decide for itself what to do with a violation.

## Concepts

`UsesRule`
: The `uses` directive of an exported package names the packages that its types expose, so an importer must see them from the same provider as the exporter does.
The rule computes the packages a resource sees and follows the `uses` directives from there, transitively.
A package that has a single provider in the whole resolve can not be inconsistent, the `UsesIndex` of the `index` module tells which packages are contested, and everything that does not lead to one is not looked at.
A package that is split over several required bundles is seen partly by a consumer of one of them, that is consistent as long as one side contains the other.

`SubstitutionRule`
: A bundle that exports and imports the same package keeps its export only if the import is wired to that export, otherwise the export is discarded (Core specification, "Importing and Exporting the Same Package").
The rule reports an import that is wired to an export which is discarded.
The `SubstitutionIndex` of the `index` module knows up front which exports can be discarded.

Both rules wrap the index they read and return a `UsesViolation` of the `candidates` module for the first problem they find wrapped in an `Optional`, which is empty if the class space is consistent, and never change the graph.

A `UsesViolation` tells more than the package that is inconsistent: `getSeen` is what the resource sees of the package, `getImplied` what the capabilities at the end of the chains see of it and therefore expect.
`getChains` returns a `UsesChain` for each of them, which tells why the resource sees that capability: its viewer, the capability, the chain of the capability whose `uses` led there (`getParent`) and the requirement of the viewer it is seen through (`getRequirement`, looked up when asked).
The requirements along a chain are the places where a different provider changes the view.
The rule records this while it walks, nothing is computed for it, so a strategy learns which capabilities expect the other provider and through which providers the resource reaches them without walking the graph again.

`ClassSpaceRules`
: Composes both rules over a set of `ResolverResource`s, which together with their candidates form the candidate graph, and the subset of them that has to be resolved.
It builds the `SubstitutionIndex` (over the included candidates, with the mandatory resources as certain) and the `UsesIndex` itself, and `violationOf` reports a discarded export before a uses violation.

```java
ClassSpaceRules rules = new ClassSpaceRules(resources, mandatory);
Optional<UsesViolation> violation = rules.violationOf(resource);
```

Or use a single rule directly:

```java
UsesRule rule = new UsesRule(new UsesIndex(resources, candidateSource, expand));
for (ResolverResource resource : graph) {
    Optional<UsesViolation> violation = rule.check(resource);
}
```

## How to use

Build the candidate graph and the index from the same resources, select providers, then ask the rules per resource.
Checks of different resources do not depend on each other, so they can run concurrently as long as nothing changes the graph in the meantime (see the thread safety notes of the `candidates` module).
What a resolver does with a violation (rejecting a candidate, trying another one, reporting a failure) is not part of this module.

The module depends on `candidates` and `index` and is compiled for Java 8.

## Theory and references

- OSGi Core, "Class Space Consistency" and the `uses` directive of `Export-Package`.
- OSGi Core, "Importing and Exporting the Same Package" (substitution).
