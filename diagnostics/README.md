# Resolver Diagnostics

An optional API through which a resolver explains the decisions it made in a resolve operation, whether it succeeded or not.
A `ResolveContext` or a `ResolutionException` only tells what was resolved or that something failed, to learn why one has to redo the analysis and know how the resolver works.
With diagnostics the resolver tells it.

## Purpose

The API is deliberately small and depends on nothing but the `org.osgi.resource` types, so any resolver can publish diagnostics and any tool can consume them without a dependency on a resolver implementation.
Resolvers that do not know the API simply never call it, so adding it to a `ResolveContext` is always safe.
The module is compiled for Java 8, so it can be used from any resolver or tool regardless of its Java level.

The explanations are human readable text for diagnostic purposes, their format is not specified and must not be parsed.

## How it works

`DiagnosticsListener`
: Implemented by a `ResolveContext` that is interested in the diagnostics.
A resolver that supports the API calls `publishDiagnostic` once per resolve operation, before it returns or throws, for successful as well as for failed operations.

`Diagnostics`
: The explanation of one resolve operation as an object that can be queried.
`getMessage(Resource)` tells why a resource was resolved with certain wires or could not be resolved, `getMessage(Requirement)` which of the possible providers was chosen and why the others were not, and `getMessage(Capability)` for example why an export is not used because its resource is wired to another provider of the same package.
The resource of a requirement or capability identifies the owner.
An empty result means the resolver has nothing to say about the element.

```java
class ExplainingContext extends ResolveContext implements DiagnosticsListener {

    private Diagnostics diagnostics;

    @Override
    public void publishDiagnostic(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    String why(Resource resource) {
        return diagnostics.getMessage(resource).orElse("nothing to say");
    }

    // the remaining ResolveContext methods are omitted
}
```

## How to extend

The API grows step by step, every addition must be useful for resolvers other than the one it was written for.
New queries are added as methods of `Diagnostics` that return `Optional` so a resolver may have nothing to say.
It contains interfaces only, implementations live in the resolver.
The package is versioned with `@Version` and uses `@ConsumerType` and `@ProviderType` as the OSGi specifications do.

## References

- [OSGi Resolver Service Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html)
- [Add optional Diagnostics API to explain resolution results](https://github.com/osgi/org.osgi.service.resolver/pull/45), the proposal for the specification this API follows

The API is meant as a working example for that proposal.
Using it in resolvers and in the test suite shows whether the queries are sufficient and where they need to change.
It is not part of the OSGi specification and can change until the proposal is settled.
