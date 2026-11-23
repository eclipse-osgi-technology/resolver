# resolver-repair

A resolver that decides the detected class space conflicts with a conflict directed repair search.
The class space of a bundle is the set of packages it can load, a `uses` conflict is a class space in which a package is seen from two different providers.
The phases around the strategy are shared with other resolvers, so the search can be compared with other approaches on the same rules.

## Architecture

`RepairResolver` is a `PhasedResolver` from `resolve-phases` that uses a `RepairSearch` as its `UsesConflictStrategy` from `conflicts`.
`RepairResolver` and `RepairSearch` are the public types, everything else in the module is package private:

- `RepairSearch` runs the rounds of the class space check, repairs the resources that are inconsistent and falls back to the last resorts.
- `RepairState` is the state of one run: what the search remembers while it runs, the strategy itself keeps no state.
- `ConflictSearch` is the depth first search that makes the class space of one resource consistent, for a fixed number of optional imports it may leave unwired.
- `Choices` tells which choices may be changed for a conflict and in which order they are tried.
- `CandidateSwap` and `SwapPath` record the change of a selected candidate and the changes on the way to the conflict, including the reason that the diagnostics show.

## Approach

1. The phases select the first candidate of every requirement, resolve what can be resolved and check the class space of every resolved resource, if nothing is inconsistent the search is never asked.
2. A resource with a violation is repaired by changing a choice that can influence the violated package, which the `uses` graph tells: the imports behind the conflicting resource first, then its own.
3. All imports of a bundle that the old and the new provider both offer move together, so a bundle is never exposed to two providers that export the same packages.
4. A change is kept if everything that can see it is consistent or repairable again, otherwise it is taken back with a `Checkpoint` of the candidate graph (module `candidates`) and the next one is tried.
5. If no change of providers helps, an optional import that takes part in the conflict is left unwired, as few as possible, and then a resource that nobody asked for is given up (a sacrifice) if that loses less than failing the conflicting one.
6. A conflict that no choice can remove fails at once.
7. A resource that no repair helps fails with its violation, the reasons of all changes are recorded at the candidates (`Candidate.defer` and `Candidate.select`) and shown in the diagnostics.

## Scope

The search is a constructive first fit (the highest priority candidate of every requirement) followed by a local repair of the conflicts that the checks detect, restricted to the requirements that the `uses` graph connects to the conflicting resource.
It searches those requirements systematically, and a conflict is repaired in isolation, the repairs of earlier conflicts are kept.
This makes it fast where conflicts are few and local.
The `uses` checks are the consistency predicate and the graph that tells where to look.

## Theory and references

- OSGi Core, "Class Space Consistency" and the `uses` directive for the constraints a repair has to satisfy.
- The search follows the ideas of [min-conflicts](https://en.wikipedia.org/wiki/Min-conflicts_algorithm) repair and of [large neighbourhood search](https://en.wikipedia.org/wiki/Large_neighborhood_search): start from a complete assignment, change the choices that take part in a conflict, systematically inside a neighbourhood.
- The depth first search with a growing number of unwired imports is [iterative deepening](https://en.wikipedia.org/wiki/Iterative_deepening_depth-first_search), the choice of what to change next is [conflict directed](https://en.wikipedia.org/wiki/Backjumping).
