# Resolver Verifier

A resolver independent judge for the result of a resolve operation.
Given the wires a resolver produced, the verifier answers whether the result is correct according to the OSGi specification, and if not, why.

## Purpose

Resolvers search for a solution, the verifier never does.
It only looks at a finished result, so it is usable with any resolver and does not depend on any resolver implementation.
It is intended as a test verifier for resolver developers, for test suites that compare implementations and for tools that want to double check a wiring.
It is deliberately simple, it is not meant for production resolving.

The module has no dependencies besides the OSGi resource and framework namespace APIs and is compiled for Java 8, so it can be used from any resolver or tool regardless of its Java level.

## How it works

There are two checks and a helper, all take the plain `org.osgi.resource` model (`Resource`, `Wire`) as input.
The checks return a list of human readable violations, an empty list means the result is fine.

`WiringVerifier`
: Checks that a result is a well formed wiring.
  All mandatory resources are part of the result and nothing that was not requested is.
  Every mandatory and effective requirement is wired (including those of attached fragments for their host), cardinality is respected and each wire matches its requirement and ends in a provider that is part of the result.
  It does not decide whether the *right* candidate was chosen, that is the business of a test scenario.

`ConsistencyVerifier`
: Checks that all class spaces of a result are consistent.
  It determines for every resource which source provides each visible package (imports, required bundles including reexports, own exports) and verifies the `uses` constraints along the wires: whoever consumes a capability must see every package it `uses` from the same source as the provider does.
  Split packages and `uses` on generic capabilities are covered.

`ClassSpace`
: The class space the `ConsistencyVerifier` is based on.
  It tells which packages a resource sees, which capabilities make them visible and which resources they really come from, following the search order imports, required bundles (including reexports) and own exports.
  The packages of an attached fragment belong to its host.

```java
Map<Resource, List<Wire>> result = ...; // wires produced by any resolver
List<String> problems = new ConsistencyVerifier(result).violations();
```

## Role in the project

The verifier is the neutral voice of this project when a result has to be judged as right or wrong, and we expect it to be an entry point for external reviews and discussions.

- If a wiring problem goes undetected, the verifier has to be updated accordingly, and the first step is a test case in `ConsistencyVerifierTest` or `WiringVerifierTest` that shows the problem.
- A change of the verdict of the verifier must be justified by OSGi experts and/or clarified with the specification committee, an implementation merely behaving differently is not a reason.

## Theory and references

In testing terms this is a [test oracle](https://en.wikipedia.org/wiki/Test_oracle), a mechanism that tells whether an output is correct without computing it.
It is related to the theoretical concept of an [oracle machine](https://en.wikipedia.org/wiki/Oracle_machine), a black box that answers a decision question.
Checking a given result is far simpler than finding one, which is why the verifier can stay small and independent of any resolver.
