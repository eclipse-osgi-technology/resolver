# resolver-permutation

A resolver that decides the detected class space conflicts the way the Apache Felix resolver does.
It is a baseline: the phases around the strategy are shared with other resolvers, so the permutation approach can be compared with other strategies without the class space checks of the Felix implementation dominating the result.

## Architecture

`PermutationResolver` is a `PhasedResolver` from `resolve-phases` that uses a `PermutationSearch` as its `UsesConflictStrategy` from `conflicts`.
Everything else in the module is package private:

- `PermutationSearch` runs the search and fails the resources that are left inconsistent.
- `Permutation` and `PermutationQueue` hold the delta of dropped candidates and the order in which permutations are tried, every one only once.
- `ConsistencyCheck` checks a permutation and finds the requirements to blame for a conflict.
- `GraphState` applies and restores a permutation on the candidate graph.
- `Fault` and `PermutationType` describe a conflict and the reason a permutation was queued.

## Approach

The initial selection (the first candidate of every requirement) is the first permutation.
A permutation is consistent if the class space of every root resource is.
If it is not, new permutations are queued and the next one is tried:

- If an export and an import of a package conflict, or two imports do, the requirements on the way from the resource to the conflicting capabilities are blamed.
  For each of them a permutation is queued that drops the candidate in use, from the side of the provider (the chain is walked from the last requirement to the first) and from the side of the resource (from the first to the last).
- If the conflict is between imports of the resource itself, permutations that drop the own import are queued as well.
- A requirement that accepts several providers (multiple cardinality) takes any of them, the ones that make the class space inconsistent are left out.
- Imports whose package is exported by the resource itself are substituted: permutations that move the import to the next candidate are queued.

The queue is ordered like in Felix: permutations for the uses conflict first, then for imports, then for substitutions.
A candidate is only dropped if its requirement has another candidate or is optional, and an export is never dropped when it is the only way for a dependent resource to be resolved.
If no permutation is consistent, the resources that were inconsistent in the best permutation fail.
If some of them were only asked for optionally, only those fail and the search starts again.

## Theory and references

- OSGi Core, "Class Space Consistency" and the `uses` directive for the constraints a permutation has to satisfy.
- [Apache Felix resolver](https://github.com/apache/felix-dev/tree/master/resolver) for the permutation of candidates that is followed here.

Permutating the choices that are to blame is a form of [backtracking](https://en.wikipedia.org/wiki/Backtracking) with [conflict directed](https://en.wikipedia.org/wiki/Backjumping) selection of the next choice: only the candidates involved in a conflict are changed instead of every combination.
