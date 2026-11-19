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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;
import org.osgi.framework.namespace.HostNamespace;

/**
 * A {@link UsesConflictStrategy} that decides the choices a class space conflict depends on with a pseudo boolean
 * solver (Sat4j).
 * If the selection by priority left class spaces inconsistent, the open choices (resolved resources, selected
 * candidates and the alternatives behind them, fragment attachments, substitutable exports) are encoded once with the
 * class space rule ({@link UsesSolver}) and solved with the objective: every optional resource resolved, nothing
 * given up or detached, every optional requirement wired, the candidate ranked first by the context.
 * The solution is applied to the graph with reasons and checked with the rule again, anything the encoding let
 * through fails as it would without a solver.
 * <p>
 * The solver decides all coupled choices at once and optimally, which makes the policies an objective one can read.
 * The reason of a changed choice is the conflict it takes part in, found through the uses graph.
 */
public final class Sat4jStrategy implements UsesConflictStrategy {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private final long timeoutSeconds;

    public Sat4jStrategy() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param timeoutSeconds the time the solver may take per resolve, the best solution found so far is used after it
     */
    public Sat4jStrategy(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void enforceUses(UsesSession session) {
        Collection<ResolverResource> dirty = session.getResources();
        boolean solved = false;
        boolean checkAll = false;
        while (true) {
            Map<ResolverResource, UsesViolation> violations = violationsOf(session, dirty);
            if (violations.isEmpty()) {
                return;
            }
            List<ResolverRequirement> changed = new ArrayList<>();
            boolean failed;
            if (!solved) {
                solved = true;
                Solution solution = new UsesSolver(session, timeoutSeconds).solve(violations);
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
     * The class space rule applied to the resolved resources of a collection, in parallel.
     *
     * @return the violation of each resource that is not consistent, in the order of the collection
     */
    private static Map<ResolverResource, UsesViolation> violationsOf(UsesSession session,
            Collection<ResolverResource> resources) {
        List<ResolverResource> resolved = resources.stream().filter(r -> r.getState() == ResolveState.RESOLVED)
                .collect(Collectors.toList());
        List<UsesViolation> found = resolved.parallelStream().map(r -> session.violationOf(r).orElse(null))
                .collect(Collectors.toList());
        Map<ResolverResource, UsesViolation> violations = new LinkedHashMap<>();
        for (int i = 0; i < resolved.size(); i++) {
            if (found.get(i) != null) {
                violations.put(resolved.get(i), found.get(i));
            }
        }
        return violations;
    }

    /**
     * Applies what the solver decided: changed selections, fragments it detached and resources it gave up, which fail
     * with their violation (or with the host requirement for a fragment no host is left for).
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
        for (ResolverResource fragment : session.getResources()) {
            if (fragment.isFragment() && fragment.getState() == ResolveState.RESOLVED
                    && fragment.getHosts().isEmpty()) {
                for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
                    if (HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                        fragment.markFailed(requirement);
                        failed = true;
                    }
                }
            }
        }
        return failed;
    }
}
