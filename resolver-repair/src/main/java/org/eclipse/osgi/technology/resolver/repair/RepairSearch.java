/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.repair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;

/**
 * A {@link UsesConflictStrategy} that decides the open class space conflicts with a conflict directed depth first
 * search.
 * For every resource whose class space is inconsistent:
 * <ol>
 * <li>Another candidate is chosen for an import or required bundle, first behind the imports of the conflicting
 * resource (its providers adapt to it), then for its own imports.
 * The imports of a bundle that the old and the new provider both offer move together.</li>
 * <li>A change is kept if the resource and everything that can see the change is consistent or repairable again,
 * otherwise it is taken back.</li>
 * <li>If no change of providers helps, an optional import of the resource that takes part in the conflict is left
 * unwired, as few as possible: the search allows none first and one more only if it had to hold one back.</li>
 * <li>As the last choice a resource that nobody asked for is given up (a sacrifice) if that loses less than failing
 * the conflicting one.</li>
 * </ol>
 * The reason of every change is recorded at the candidates with {@code Candidate.defer} and
 * {@code Candidate.select}, so the diagnostics tell why a provider was replaced and which conflict caused it.
 * The strategy keeps no state, the state of one run is kept apart from it.
 */
public final class RepairSearch implements UsesConflictStrategy {

    @Override
    public void enforceUses(UsesSession session) {
        enforce(new RepairState(session));
    }

    private static void enforce(RepairState state) {
        // the first round looks at everything, later rounds only at what can see a changed wiring
        Collection<ResolverResource> dirty = state.session.getResources();
        while (true) {
            Map<ResolverResource, UsesViolation> violations = state.session.violationsOf(dirty);
            state.inconsistent.clear();
            state.inconsistent.addAll(violations.keySet());
            boolean failed = false;
            List<ResolverRequirement> changed = new ArrayList<>();
            for (Map.Entry<ResolverResource, UsesViolation> entry : violations.entrySet()) {
                ResolverResource resource = entry.getKey();
                if (resource.getState() != ResolveState.RESOLVED) {
                    continue;
                }
                // an earlier repair can have removed the conflict
                UsesViolation violation = changed.isEmpty() ? entry.getValue() : state.session.violationOf(resource).orElse(null);
                if (violation == null) {
                    continue;
                }
                if (repair(state, resource, violation, changed)) {
                    state.inconsistent.remove(resource);
                    continue;
                }
                if (sacrifice(state, resource, violation, changed)) {
                    failed = true;
                    continue;
                }
                state.session.fail(resource, violation);
                failed = true;
            }
            if (!failed && changed.isEmpty()) {
                return;
            }
            if (failed) {
                changed.addAll(state.session.propagateFailures());
            }
            dirty = state.session.affected(changed);
        }
    }

    /**
     * Repairs a uses conflict of a resource by choosing differently, the changes are only kept if they work out.
     * First the imports behind its own that decide about the packages its candidates use are changed (the providers adapt to the resource),
     * then its own package imports. A change is kept if the resource and everything that can see the change
     * is consistent afterwards, a resource that is not can itself be repaired, else everything is taken back.
     * Nothing is changed if there is no way.
     */
    private static boolean repair(RepairState state, ResolverResource resource, UsesViolation violation,
            List<ResolverRequirement> changed) {
        SwapPath path = new SwapPath();
        if (search(state, resource, violation, path)) {
            for (ResolverRequirement requirement : path.requirementsSince(0)) {
                changed.add(requirement);
                state.settled.add(requirement);
            }
            return true;
        }
        return false;
    }

    /**
     * The search is repeated with a growing number of optional imports it may leave unwired, so the repair that
     * takes the least away is found first.
     */
    private static boolean search(RepairState state, ResolverResource resource, UsesViolation violation, SwapPath path) {
        for (int depth = 0;; depth++) {
            state.cutOff = false;
            if (new ConflictSearch(state, resource, violation, path, depth).run()) {
                return true;
            }
            if (!state.cutOff) {
                // no optional import was held back by the depth, so a larger one changes nothing
                return false;
            }
        }
    }

    /**
     * Last resort for a resource that can not be repaired: if a change that makes it consistent breaks only
     * resources that were not asked for explicitly and can not be repaired themselves, those are given up instead
     * if that loses less, i.e. fewer resources fail with them (a consumer rather than its provider).
     * Nothing is given up for a conflict of something that is needed, that fails the resolve as before.
     */
    private static boolean sacrifice(RepairState state, ResolverResource resource, UsesViolation violation,
            List<ResolverRequirement> changed) {
        Set<ResolverResource> own = state.dependents(Collections.singletonList(resource));
        // something that is needed is never given up, but what was not asked for may be given up for it
        boolean needed = own.stream().anyMatch(state::isMandatory);
        if (needed && state.session.getOptional().isEmpty()) {
            return false;
        }
        SwapPath path = new SwapPath();
        List<ResolverResource> given;
        try (Checkpoint attempt = state.session.checkpoint()) {
            boolean repaired;
            state.startSacrificing();
            try {
                repaired = search(state, resource, violation, path);
            } finally {
                given = state.stopSacrificing();
            }
            if (repaired && !given.isEmpty()) {
                Set<ResolverResource> lost = state.dependents(given);
                repaired = lost.stream().noneMatch(state::isMandatory) && (needed || lost.size() < own.size());
            }
            if (!repaired) {
                attempt.rollback();
                return false;
            }
        }
        changed.addAll(path.requirementsSince(0));
        for (ResolverResource owner : given) {
            UsesViolation broken = state.session.violationOf(owner).orElse(null);
            if (broken != null) {
                owner.markFailed(broken);
                changed.addAll(owner.getResolverRequirements());
            }
        }
        return true;
    }
}
