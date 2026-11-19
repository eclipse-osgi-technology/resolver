/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.permutation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;

/**
 * A {@link UsesConflictStrategy} that decides the detected class space conflicts the way the Felix resolver does:
 * the initial selection is a permutation, and the first one that has a consistent class space for every resource
 * wins.
 * A permutation that is not consistent adds new permutations (see {@link ConsistencyCheck}): for a conflict, the
 * candidates of the requirements that are to blame for it are dropped, both from the side of the resource and from
 * the side of the provider, and the imports on the way to the conflict are backtracked.
 * The permutations are tried in a fixed order (see {@link PermutationQueue}) and every one only once.
 * If none is left, the resources that were inconsistent in the best permutation fail, if only resources nobody asked
 * for explicitly are lost the search starts again without them.
 * The strategy keeps no state, the state of one run is a {@link GraphState}.
 */
public final class PermutationSearch implements UsesConflictStrategy {

    @Override
    public void enforceUses(UsesSession session) {
        while (search(session)) {
            // resources were lost that nobody asked for, the others may be consistent now
        }
    }

    /**
     * @return true if resources were failed and the search has to be repeated
     */
    private static boolean search(UsesSession session) {
        try (GraphState state = new GraphState(session)) {
            return search(session, state);
        }
    }

    private static boolean search(UsesSession session, GraphState state) {
        PermutationQueue queue = new PermutationQueue(state);
        queue.add(PermutationType.USES, new Permutation());
        Map<ResolverResource, Fault> best = null;
        Fault last = null;
        Permutation next;
        while ((next = queue.next()) != null) {
            ConsistencyCheck check = new ConsistencyCheck(session, state, queue, next);
            if (check.run()) {
                commit(state, check);
                return false;
            }
            if (check.getUnresolvable() != null) {
                last = check.getUnresolvable();
            }
            Map<ResolverResource, Fault> faulty = check.getFaulty();
            if (!faulty.isEmpty()) {
                last = faulty.values().iterator().next();
                // the first inconsistent permutation tells the resources, later ones only if fewer are lost
                if (best == null || faulty.size() < best.size()) {
                    best = faulty;
                }
            }
        }
        state.restore();
        Collection<Fault> faults = best == null ? new ArrayList<Fault>() : best.values();
        if (best == null && last != null) {
            faults.add(last);
        }
        return fail(session, faults);
    }

    private static void commit(GraphState state, ConsistencyCheck check) {
        state.restore();
        state.apply(check.getSelection(), true);
        for (Map.Entry<ResolverRequirement, Set<Candidate>> entry : check.getMultiple().entrySet()) {
            for (Candidate candidate : entry.getValue()) {
                candidate.defer("Not used to avoid an inconsistent class space, " + entry.getKey().getOwner().getName()
                        + " takes any of the providers");
            }
        }
    }

    private static boolean fail(UsesSession session, Collection<Fault> faults) {
        Map<ResolverResource, UsesViolation> failed = new LinkedHashMap<>();
        for (Fault fault : faults) {
            ResolverResource resource = fault.getResource();
            if (resource.getState() == ResolveState.RESOLVED && !failed.containsKey(resource)) {
                failed.put(resource, fault.getViolation());
            }
        }
        // as in Felix, if optional resources are inconsistent only those are dropped and the rest is tried again
        Map<ResolverResource, UsesViolation> optional = new LinkedHashMap<>();
        for (Map.Entry<ResolverResource, UsesViolation> entry : failed.entrySet()) {
            if (session.getOptional().contains(entry.getKey())) {
                optional.put(entry.getKey(), entry.getValue());
            }
        }
        if (!optional.isEmpty()) {
            failed = optional;
        }
        boolean changed = false;
        for (Map.Entry<ResolverResource, UsesViolation> entry : failed.entrySet()) {
            ResolverResource resource = entry.getKey();
            if (resource.getState() != ResolveState.RESOLVED) {
                continue;
            }
            session.fail(resource, entry.getValue());
            changed = true;
        }
        session.propagateFailures();
        if (!changed) {
            return false;
        }
        for (ResolverResource mandatory : session.getMandatory()) {
            if (mandatory.getState() == ResolveState.FAILED) {
                return false;
            }
        }
        return true;
    }
}
