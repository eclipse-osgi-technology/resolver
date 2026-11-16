# Resolver Phases

The phases of a resolve operation in the order they run, with the resolution of the detected class space conflicts left to a pluggable strategy.

## Purpose

A resolve operation is more than the decision of conflicting choices.
Resources and their providers have to be found, fragments have to be attached, rules have to be applied, what can not be resolved has to be found out and explained, and the result has to be turned into wires.
This module splits that into small phases with explicit inputs and outputs, so a resolver is glue that calls them in order and can reuse or replace any of them.
Only the resolution of the class space conflicts is not part of the module, it is delegated to a `UsesConflictStrategy` of the `conflicts` module.

## Concepts

`ResolverGraph`
: The resources of one operation in the order they were discovered, and which of them the resolve context asked for.
It is what the phases hand over to each other, the state of every decision lives in the candidate graph of the resources (see the `candidates` module).

`DiscoveryPhase`
: Builds the graph from a `ResolveContext`: the requested resources and everything reachable from their effective requirements through the providers the context knows.
It is the only phase that asks the context for providers, apart from the placement of hosted capabilities.

`FragmentAttachmentPhase`
: Selects the fragments that are attached to a host, only the highest version of a symbolic name, and places the capabilities a fragment provides on each of its hosts as the context decides.
After the selection it attaches the selected fragments, so the imports of a fragment become part of the class space of the host.

`ClassSpacePhase`
: Builds the `ClassSpace` of the graph: the `ClassSpaceRules` of the `classspace-rules` module and the `UsesGraph` over their indices.
It adds what changes the graph, which the rules never do: dropping exports that nobody needs.
The `ClassSpace` it returns explains substitutions once the conflicts are decided.

`ResolvabilityPhase`
: Decides which resources can be resolved at all, with local checks and a fixpoint across resources, and then records which provider a requirement selects and what became of the other candidates.

`ConflictPhase` and `ResolverUsesSession`
: The phase lets a `UsesConflictStrategy` resolve the detected class space conflicts.
The session is what the strategy sees of the operation, an implementation of the `UsesSession` of the `conflicts` module.

`FailurePhase` and `WiringPhase`
: Derive the exception that tells why a mandatory resource failed, or the wires of the resolved resources that are needed by the requested ones.

`ResolveOperation`
: Calls the phases in that order.

`PhasedResolver`
: A `Resolver` that runs a `ResolveOperation` with a given `UsesConflictStrategy` and publishes the diagnostics of every outcome to a context that implements `DiagnosticsListener`.
A resolver with a decision procedure is a subclass that creates the strategy for a context.
Phases that are run in one step have a constructor with their input and a `run()`.

## How to use

```java
public final class MyResolver extends PhasedResolver {

    @Override
    protected UsesConflictStrategy createStrategy(ResolveContext context) {
        return new MyStrategy();
    }
}
```

A resolver that needs other behavior calls the phases itself, `ResolveOperation` is the reference for the order.
Every phase works on the `ResolverGraph` and records what it decides in the candidate graph, so the explanation of a result, a failure or a diagnostic, is derived from there and can not deviate from what was decided.

The diagnostics published by a `PhasedResolver` explain the resolve of every resource: why a requirement has no provider, why a candidate was not used, to which host a fragment is attached.
What a strategy records for its own decisions shows up in the explanation as well.

Resources, requirements and capabilities are compared by identity, and results are in a stable order, a phase that is replaced has to keep that.

The module depends on `candidates`, `classspace-rules`, `conflicts`, `diagnostics`, `failures` and `index` and is compiled for Java 8.
Dynamic resolving is not supported yet, `resolveDynamic` fails.

## Theory and references

- OSGi Core, "Resolving Process", "Fragment Bundles" and "Class Space Consistency" for what a resolve has to achieve.
- OSGi Resolver Service specification, `ResolveContext` and "Mandatory and Optional Resources" for the contract with the context.

The phases follow the pattern of a [pipeline](<https://en.wikipedia.org/wiki/Pipeline_(software)>) in which every stage hands its result to the next one, here a shared graph.
`DiscoveryPhase` and the selection of the resources that are wired are [reachability](https://en.wikipedia.org/wiki/Reachability) problems solved by a [breadth-first search](https://en.wikipedia.org/wiki/Breadth-first_search) from the requested resources.
`ResolvabilityPhase` starts optimistic, assumes every resource can be resolved and removes those that lose a mandatory requirement until nothing changes, which computes a [fixpoint](<https://en.wikipedia.org/wiki/Fixed_point_(mathematics)>) in the way [constraint propagation](https://en.wikipedia.org/wiki/Local_consistency) removes values that can not take part in a solution.
The conflicts that this propagation detects but can not decide are the part that needs search, they are resolved by a strategy, see the `conflicts` module.
