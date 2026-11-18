# Resolver Candidates

The graph a resolver works on while it decides which provider satisfies which requirement.
It records, for every requirement, which capabilities are possible, which one is chosen and why the others are not.

## Purpose

The OSGi resource API (`Resource`, `Requirement`, `Capability`) is immutable and can not carry the state of a resolution.
A resolver needs a place to keep that state.
This module offers wrappers around the given objects for that purpose.
They can be used wherever the OSGi API is expected and additionally

- cache decoded information such as the `uses` directive of a capability,
- know their owner, so a requirement or capability finds the resource it belongs to,
- carry the resolve state of a resource and the candidates of a requirement.

Rules, search strategies and tools can share this one graph instead of each building its own.

## Concepts

`ResolverResource`, `ResolverRequirement`, `ResolverCapability`
: Wrappers with identity semantics.
They are looked up through the original object, never compared by `equals`, which is deep and expensive for many implementations.
A capability that is hosted (the host exposes a capability of its fragment) has an _owner_ (the host) that differs from the resource that _declared_ it.

`Candidate`
: A capability offered for a requirement with a `CandidateState`: `AVAILABLE` (offered), `SELECTED` (chosen), `REJECTED` (excluded for good) or `DEFERRED` (excluded only because another candidate is preferred).
A rejected candidate keeps the reason, so a result can always be explained.
A deferred candidate is only used if the chosen one causes a conflict.

`ResolveState`
: `PENDING`, `RESOLVED` or `FAILED` per resource, a failed resource names the requirement or the `UsesViolation` that prevents its resolution.

`UsesViolation`
: Why no consistent class space exists: the package, the resource that would see it and the capabilities it would see at once.

`PackageViewCache`, `PackageView`
: What a resource sees of the packages on the current selection of candidates: what it imports and exports, what it gets through required bundles and the combined sources of a package.
The cache belongs to one resolve and is handed to all its resources, so the views of all resources are computed once and shared by every check until a candidate, a resolve state or the attached fragments change.
Code that changes the graph in another way has to call `PackageViewCache#invalidate`.

## Thread safety

The graph is not thread safe.
Its structure is set up first and does not change afterwards, only the state does: the resolve state of a resource and the state of a candidate.
The views of a `PackageViewCache` can be requested concurrently, but never while the state changes.
The state is kept in plain fields, so a change must not overlap with any other access to the same object.
Callers that work concurrently have to separate reading and changing into phases, with a happens-before edge between them, for example by waiting for all tasks of a phase before the next one starts.

The module has no dependencies besides the OSGi APIs and is compiled for Java 8.

## Theory and references

- OSGi Core, "Resolving Process" and "Bundle Dependencies", for the semantics of requirements, capabilities and the `uses` directive.
- OSGi Resolver Service specification, `ResolveContext`, `Wire` and `HostedCapability`.
