# Resolver Conflicts

Allows a resolver to use a pluggable strategy to resolve the class space conflicts that are detected for a selection.

## Purpose

The rules of the `classspace-rules` module tell whether a selection of providers is consistent, but not what to do if it is not.
Several procedures can decide that: a search that changes one choice at a time and re-checks what can see the change, a constraint solver that is handed all open choices of the conflicts, or a combination.
This module is the contract between the part of a resolver that discovers, selects and explains and such a procedure, so a procedure can be written, replaced or compared without knowing the rest of a resolver, and a resolver can offer several of them.

## Concepts

`UsesConflictStrategy`
: The decision procedure, one method `enforceUses(UsesSession)`.
It is called once after the providers are selected and has to leave every resolved resource with a consistent class space or failed.
Whatever it decides is recorded in the candidate graph (the states and reasons of the candidates), because the explanation of a result is derived from there.

`UsesGraph`
: The uses constraints seen from the choices: an import (or a bundle requirement) depends on the imports that decide where the packages used by its candidates come from.
It is built once from a `UsesIndex` and a `CandidateSource`, only over packages that reach a contested one, and does not depend on a selection.
`affected` tells which imports have to be looked at again if some choices change, `reachable` which choices a conflict can depend on.
Both return each import once, in the order they are found.

`UsesSession`
: What a strategy sees of the resolve operation: the resources, the `UsesIndex`, `UsesGraph` and `SubstitutionIndex` built from them, and the operations it may use.
`violationOf` applies the class space rules to the current selection and returns the first violation of a resource, if any.
`fail` fails a resource with a violation, `propagateFailures` removes what depended on a failed provider and fails the resources that lost a mandatory requirement, and `affected` tells which resources have to be checked again after some choices changed.

```java
public final class FailingStrategy implements UsesConflictStrategy {

    @Override
    public void enforceUses(UsesSession session) {
        for (ResolverResource resource : session.getResources()) {
            if (resource.getState() == ResolveState.RESOLVED) {
                session.violationOf(resource).ifPresent(violation -> session.fail(resource, violation));
            }
        }
        session.propagateFailures();
    }
}
```

## How to use

A strategy that repairs conflicts changes the selection through the `Candidate` of the graph (`defer`, `select`, `restore`), asks `UsesGraph` which choices matter for a conflict (`reachable`) and what a change can affect (`affected`, or `UsesSession.affected` for the resources), checks again with `violationOf` and takes the change back if it did not help.
The tests show such a strategy on a small graph.
Checks of different resources do not depend on each other, so `violationOf` may be called concurrently as long as nothing changes the graph at the same time (see the thread safety notes of the `candidates` module).
Resources, requirements and capabilities are compared by identity, and results are in a stable order, a strategy has to keep that, or its results differ from run to run.

A resolver that wants to be used with strategies implements `UsesSession` on top of its own state and calls the strategy after the selection.
It decides which strategy it uses, and a strategy decides nothing about discovery, selection or the explanation of a failure.

The module depends on `candidates` and `index` and is compiled for Java 8.

## Theory and references

- OSGi Core, "Class Space Consistency" and the `uses` directive of `Export-Package`.
- The `index` module for the `UsesIndex` the graph is built from.

The `UsesGraph` is the [constraint graph](https://en.wikipedia.org/wiki/Constraint_satisfaction_problem) of the problem: its nodes are the choices (the variables), and an edge says that what one choice selects can make the class space of another inconsistent (a constraint between them).
`affected` and `reachable` are [reachability](https://en.wikipedia.org/wiki/Reachability) queries on this [directed graph](https://en.wikipedia.org/wiki/Directed_graph) in both directions, answered by a [breadth-first search](https://en.wikipedia.org/wiki/Breadth-first_search), which is why every choice is found once and nearest first.
Asking what has to be checked again after a change is an [impact analysis](https://en.wikipedia.org/wiki/Change_impact_analysis) over a [dependency graph](https://en.wikipedia.org/wiki/Dependency_graph).

The detected conflicts of a selection can be understood as a constraint satisfaction problem, the module only defines the contract to decide it, not how.
Which techniques a strategy builds on is described where it is implemented, so the references to them live in the README of the respective strategy module.
