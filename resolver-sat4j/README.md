# resolver-sat4j

A resolver that decides the choices a class space conflict depends on with a pseudo boolean solver ([Sat4j](https://www.sat4j.org/)).
The phases around the strategy are shared with other resolvers, so the solver can be compared with other approaches on the same rules.

## Architecture

`Sat4jResolver` is a `PhasedResolver` from `resolve-phases` that uses a `Sat4jStrategy` as its `UsesConflictStrategy` from `conflicts`.
Everything else in the module is package private:

- `Sat4jStrategy` runs the rounds of the class space check, calls the solver, applies the solution and checks the result with the rule again.
- `Encoding` chooses how much of the rule is handed to the solver.
- `PseudoBooleanProblem` collects variables, clauses, at most one constraints and the objective and hands them to Sat4j.
  It knows nothing about resources.
- `UsesSolver` encodes the class space rule of a `UsesSession` into a `PseudoBooleanProblem`, `SeenUse` is a helper of the encoding.
- `Solution` applies the model to the candidate graph and records the reasons in the candidates.

## Approach

1. The phases select the candidates by priority and check the class space of every resolved resource with the rules.
   If nothing is inconsistent the solver is never asked, which is the common case.
2. Otherwise the open choices are encoded as a pseudo boolean problem: which resources are resolved, which candidate each requirement is wired to and which exports are substituted.
   The fragments are attached to the hosts the phases selected them for, the solver does not decide that, see `resolve-phases`.
3. The problem is solved with an objective that is lexicographic by scaling:
   every optional resource is resolved, nothing is given up, every optional requirement is wired, and the candidate the context ranked first is preferred.
4. The solution is applied to the graph.
   A changed choice records why it was changed, naming the conflict the choice takes part in, and a resource the solver had to give up fails with its violation.
5. The result is checked with the rule again, anything the encoding let through fails as it would without a solver.

The solver decides all coupled choices at once and optimally, which makes the policies an objective one can read.
The reason of a changed choice is the conflict it takes part in, found through the `uses` graph, it is not derived from a chain of reasoning.

## Encoding the class space rule

The problem is built from the graph as the phases left it:
only resources that passed the local and cross resource checks take part, only the selected candidate and the alternatives of a requirement are offered, exports pinned by substitution are decided, and only package names that reach a contested package get variables.

The variables are:

`R(r)`, `W(c)`
: Resource `r` is resolved, candidate `c` is wired.
A mandatory requirement of a resolved resource with single cardinality is wired to exactly one of its candidates, an optional one to at most one.
A wire needs its provider resolved, a wire to a capability of a fragment is possible for the hosts the fragment is attached to.

`A(e)`
: An export that its own bundle imports as well is kept.
It is if the resource is resolved and the overlapping import is not wired elsewhere (substitution).

`D(r,p,e)`, `X(r,b)`, `I(r,p)`
: Resource `r` sees package `p` directly from export `e`, by an import, its own export or a required bundle.
`r` sees the exports of bundle `b` through Require-Bundle, including reexports, and `r` imports `p`, so what required bundles provide for `p` is shadowed.
These are the sources of the class space rule.
They are defined in both directions: a clause for every way the variable becomes true (a wire, for a fragment also the hosts it is attached to) and a clause that it is true only if one of these ways holds.

`S(r,c)`
: Resource `r` sees capability `c`, directly or implied: whoever sees a capability sees the direct sources of its exporter for every package the capability uses, and the capabilities these sources use in turn.
This is the least fixpoint of the rule, encoded as implications only and used only negatively, so a lower bound is enough.

The constraint is the containment rule of `UsesRule`:
for every capability `c` that `r` sees and every package `p` that `c` uses, a direct exporter `a` of `p` for `r` and a direct exporter `b` of `p` for the exporter of `c` that differ are forbidden together, unless an escape holds:
`a` is among the sources of the exporter as well, `b` among the ones of `r`, or `p` is split over bundles `r` requires, which is a conjunction of `X` literals.
Two direct sources of one package for the same resource are legal only as such a split.

Three things to keep when changing the encoding:

- Every auxiliary variable that occurs positively in a clause (as an escape) has to be defined in both directions, otherwise the optimiser sets it wherever that is cheaper and the encoding is too lenient.
- The view of a capability must not be defined as a union with the direct view: a bundle sees its own export, its `uses` imply its own view of the used packages, and the union variable would justify itself in a cycle.
  Keeping the implied view as a separate lower bound breaks the cycle.
- The exemption for a split package has to depend on the wires chosen, not on the candidates that exist, otherwise it is too lenient as soon as a Require-Bundle requirement has alternatives.

## Full and sliced encoding

The class space rule can be handed to the solver in two ways, the `Encoding` is an argument of `Sat4jResolver` and `Sat4jStrategy` and has to be chosen, there is no default.

- `SLICED` encodes only the packages of the conflicts found and the packages whose class space reaches them (`UsesIndex#reachingFrom`).
  The solution is tried on the graph inside a `Checkpoint`, checked with the rule and rolled back.
  If it is not consistent, the packages of its conflicts are added and the problem is solved again from the state before.
  A solution of fewer constraints that is consistent is a solution of all of them, so the result is the same as with the full encoding.
  This is a counterexample guided refinement: the encoding starts abstract and is refined by what the check finds wrong.
  It ends when the check is clean, or when a conflict is on a package that was encoded already, which is one more solve of every package.
- `FULL` encodes every package that can take part in a conflict and solves once.

The tests run the shared scenarios once for each encoding, the system property `org.eclipse.osgi.technology.resolver.sat4j.encoding` of the test engine chooses it, see the executions of the surefire plugin in the `pom.xml`.

## How to extend

The encoding is the part to change.
`UsesSolver` adds clauses to the `PseudoBooleanProblem`, a new constraint is a new clause there and a new policy a new term of the objective.
`PseudoBooleanProblem` folds the constants `TRUE` and `FALSE` away, so a clause may contain literals that are already decided.
The solver time is limited by the constructor argument of `Sat4jStrategy` (60 seconds by default), the best solution found so far is used after it.

## Theory and references

- OSGi Core, "Class Space Consistency" and the `uses` directive for the constraint the encoding states.
- [Sat4j](https://www.sat4j.org/) is the pseudo boolean solver and optimiser: Le Berre and Parrain, "The Sat4j library, release 2.2", Journal on Satisfiability, Boolean Modeling and Computation 7 (2010).
  Its default solver learns from conflicts in the way of the SAT solver MiniSat and Glucose (conflict driven clause learning, CDCL), see Marques-Silva and Sakallah, "GRASP: A Search Algorithm for Propositional Satisfiability", IEEE Transactions on Computers 48 (1999), for the technique.
  It finds the optimum by solving again with a tighter bound on the objective until no better model exists, which is where most of the time goes.
- Encoding the installation of packages as a pseudo boolean optimisation problem with a lexicographic objective is the approach of Tucker et al., "OPIUM: Optimal Package Install/Uninstall Manager", ICSE 2007, and of Eclipse p2: Le Berre and Rapicault, "Dependency Management for the Eclipse Ecosystem: Eclipse p2, Metadata and Resolution", IWOCE 2009.
  What is specific here is that the solver only decides the choices that are still open after the phases, the class space rule itself is the one of `classspace-rules`.
- The sliced encoding is an instance of counterexample guided abstraction refinement: Clarke, Grumberg, Jha, Lu and Veith, "Counterexample-Guided Abstraction Refinement", CAV 2000 (LNCS 1855).
  A model of the abstraction is checked against the full rule (here the class space check on the graph), and what the check finds wrong refines the abstraction.
  Adding the constraints a model violates only when it does is also known as lazy constraint generation.
