/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.sat4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;

/**
 * A {@link UsesConflictStrategy} that decides the choices a class space conflict depends on with a pseudo boolean
 * solver (Sat4j).
 * If the selection by priority left class spaces inconsistent, the open choices (resolved resources, selected
 * candidates and the alternatives behind them, substitutable exports) are encoded with the
 * class space rule ({@link UsesSolver}) and solved with the objective: every optional resource resolved, nothing
 * given up, every optional requirement wired, the candidate ranked first by the context.
 * How much is encoded is chosen by the {@link Encoding}.
 * The solution is applied to the graph with reasons and checked with the rule again, anything the encoding let
 * through fails as it would without a solver.
 * <p>
 * The solver decides all coupled choices at once and optimally, which makes the policies an objective one can read.
 * The reason of a changed choice is the conflict it takes part in, found through the uses graph.
 */
public final class Sat4jStrategy implements UsesConflictStrategy {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private final Encoding encoding;
    private final long timeoutSeconds;

    /**
     * @param encoding how much of the class space rule is encoded
     */
    public Sat4jStrategy(Encoding encoding) {
        this(encoding, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param encoding how much of the class space rule is encoded
     * @param timeoutSeconds the time the solver may take per resolve, the best solution found so far is used after it
     */
    public Sat4jStrategy(Encoding encoding, long timeoutSeconds) {
        this.encoding = encoding;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void enforceUses(UsesSession session) {
        Collection<ResolverResource> dirty = session.getResources();
        boolean solved = false;
        boolean checkAll = false;
        while (true) {
            Map<ResolverResource, UsesViolation> violations = session.violationsOf(dirty);
            if (violations.isEmpty()) {
                return;
            }
            List<ResolverRequirement> changed = new ArrayList<>();
            boolean failed;
            if (!solved) {
                solved = true;
                Solution solution = solve(session, violations);
                if (solution == null) {
                    // no consistent assignment of the open choices: the conflicts stand as found
                    for (Map.Entry<ResolverResource, UsesViolation> entry : violations.entrySet()) {
                        session.fail(entry.getKey(), entry.getValue());
                    }
                    failed = true;
                } else {
                    failed = apply(session, solution, violations, changed);
                    // the solution changed the graph at many places, the next round looks at everything again
                    dirty = session.getResources();
                    checkAll = true;
                }
            } else {
                // the solver's answer was applied and checked: what is still inconsistent the encoding let through
                for (Map.Entry<ResolverResource, UsesViolation> entry : violations.entrySet()) {
                    session.fail(entry.getKey(), entry.getValue());
                }
                failed = true;
            }
            if (failed) {
                changed.addAll(session.propagateFailures());
            }
            if (!checkAll) {
                dirty = session.affected(changed);
            }
            checkAll = false;
        }
    }

    /**
     * Solves with the encoding of the strategy.
     * With a sliced encoding the solution is tried on the graph and asked about the rule, if it is not consistent the
     * packages of its conflicts are added to the problem and it is solved again from the state the graph had before.
     * A solution of fewer constraints that is consistent is the solution of all of them, so nothing is decided too
     * early.
     *
     * @return the solution, {@code null} if there is none
     */
    private Solution solve(UsesSession session, Map<ResolverResource, UsesViolation> violations) {
        if (encoding == Encoding.FULL) {
            return new UsesSolver(session, timeoutSeconds, null).solve(violations);
        }
        Set<String> names = namesOf(violations);
        boolean all = false;
        while (true) {
            Solution solution = new UsesSolver(session, timeoutSeconds, all ? null : names).solve(violations);
            if (solution == null || all) {
                return solution;
            }
            Map<ResolverResource, UsesViolation> remaining;
            try (Checkpoint trial = session.checkpoint()) {
                if (apply(session, solution, violations, new ArrayList<>())) {
                    session.propagateFailures();
                }
                remaining = session.violationsOf(session.getResources());
                trial.rollback();
            }
            if (remaining.isEmpty()) {
                return solution;
            }
            // a conflict on packages the problem covered already is one the encoding lets through: ask about all
            all = !names.addAll(namesOf(remaining));
        }
    }

    /**
     * Applies what the solver decided: changed selections and resources it gave up, which fail with their
     * violation.
     *
     * @return true if a resource failed
     */
    private static boolean apply(UsesSession session, Solution solution,
            Map<ResolverResource, UsesViolation> violations, List<ResolverRequirement> changed) {
        boolean failed = false;
        for (ResolverResource given : solution.apply(changed)) {
            UsesViolation violation = violations.get(given);
            if (violation == null) {
                violation = session.violationOf(given).orElse(null);
            }
            if (violation != null) {
                session.fail(given, violation);
                failed = true;
            }
        }
        return failed;
    }

    private static Set<String> namesOf(Map<ResolverResource, UsesViolation> violations) {
        Set<String> names = new HashSet<>();
        for (UsesViolation violation : violations.values()) {
            names.add(violation.getPackageName());
        }
        return names;
    }
}
