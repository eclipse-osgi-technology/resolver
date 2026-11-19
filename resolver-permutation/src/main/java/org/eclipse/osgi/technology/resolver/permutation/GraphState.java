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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.osgi.resource.Requirement;

/**
 * The selection a search starts from, and what a {@link Permutation} makes of it.
 * A permutation is only a delta, it is shown to the graph (so the class space rule can be asked about it) by
 * {@link #apply(Permutation, boolean)} and taken back by {@link #restore()},
 * which rolls the graph back to the state the search started from.
 */
final class GraphState implements AutoCloseable {

    private static final String TRIED = "Used in a permutation of the selection";

    private final UsesSession session;
    private final Map<ResolverRequirement, List<Candidate>> usable = new IdentityHashMap<>();
    private final Checkpoint checkpoint;

    GraphState(UsesSession session) {
        this.session = session;
        this.checkpoint = session.checkpoint();
        for (ResolverResource resource : session.getResources()) {
            if (resource.getState() != ResolveState.RESOLVED) {
                continue;
            }
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                List<Candidate> list = new ArrayList<>();
                for (Candidate candidate : requirement.getCandidates()) {
                    boolean selected = candidate.getState() == CandidateState.SELECTED;
                    // everything before the selected candidate was rejected
                    boolean alternative = !requirement.isMultiple() && !list.isEmpty() && candidate.isAlternative();
                    if (selected || alternative) {
                        list.add(candidate);
                    }
                }
                if (!list.isEmpty()) {
                    usable.put(requirement, list);
                }
            }
        }
    }

    /**
     * Takes back everything that was applied.
     */
    void restore() {
        checkpoint.rollback();
    }

    /**
     * Ends the search, what was applied last stays.
     */
    @Override
    public void close() {
        checkpoint.close();
    }

    /**
     * The candidates the requirement can be wired to at all: the selected one and the alternatives behind it.
     */
    List<Candidate> usable(ResolverRequirement requirement) {
        List<Candidate> list = usable.get(requirement);
        return list == null ? Collections.<Candidate> emptyList() : list;
    }

    /**
     * The candidate the requirement is wired to if the permutation is used.
     */
    Candidate current(ResolverRequirement requirement, Permutation permutation) {
        Set<Candidate> removed = permutation.removed(requirement);
        for (Candidate candidate : usable(requirement)) {
            if (!removed.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * How many candidates the requirement can still be wired to if the permutation is used.
     */
    int remaining(ResolverRequirement requirement, Permutation permutation) {
        Set<Candidate> removed = permutation.removed(requirement);
        int count = 0;
        for (Candidate candidate : usable(requirement)) {
            if (!removed.contains(candidate)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tells if the candidate a requirement is wired to can be dropped: another one is left (or the requirement is
     * optional), and it does not take away the export a substitution keeps for a dependent that has no other choice.
     */
    boolean canRemove(ResolverRequirement requirement, Permutation permutation) {
        Candidate current = current(requirement, permutation);
        if (current == null) {
            return false;
        }
        Substitution substitution = session.getSubstitutionIndex().getSubstitution(current.getCapability());
        if (substitution != null && substitution.getOverlappingImport() == requirement) {
            for (Requirement demand : substitution.getDemand()) {
                if (!(demand instanceof ResolverRequirement)) {
                    continue;
                }
                ResolverRequirement dependent = (ResolverRequirement) demand;
                Candidate wired = current(dependent, permutation);
                if (wired != null && remaining(dependent, permutation) <= 1
                        && wired.getCapability() == current.getCapability()) {
                    return false;
                }
            }
        }
        return remaining(requirement, permutation) > 1 || requirement.isOptional();
    }

    /**
     * A copy of the permutation that does not use the candidate the requirement is currently wired to.
     *
     * @return the permutation, {@code null} if the requirement takes any provider or the candidate can not be dropped
     */
    Permutation permutate(ResolverRequirement requirement, Permutation base, Fault cause) {
        if (requirement.isMultiple() || !canRemove(requirement, base)) {
            return null;
        }
        Permutation copy = base.copy();
        copy.remove(current(requirement, base), cause);
        return copy;
    }

    /**
     * Shows the permutation to the graph.
     *
     * @param explain records the reasons of the changes at the candidates, else a short constant is used
     */
    void apply(Permutation permutation, boolean explain) {
        for (ResolverRequirement requirement : permutation.getDelta().keySet()) {
            apply(requirement, permutation, explain);
        }
    }

    /**
     * Shows what the permutation does to one requirement to the graph.
     */
    void apply(ResolverRequirement requirement, Permutation permutation, boolean explain) {
        Candidate next = current(requirement, permutation);
        Fault cause = null;
        for (Candidate removed : permutation.removed(requirement)) {
            cause = permutation.causeOf(removed);
            removed.defer(explain ? replaced(removed, next, cause) : TRIED);
        }
        if (next != null && next.getState() != CandidateState.SELECTED) {
            next.select(explain ? "Selected instead of " + permutation.removed(requirement).iterator().next()
                    .getProvider().getName() + ", that one would make the " + what(cause) + " inconsistent" : TRIED);
        }
    }

    private static String replaced(Candidate removed, Candidate next, Fault cause) {
        if (cause != null && cause.getViolation() != null && cause.getViolation().getHiddenImport().isPresent()) {
            return "Replaced by " + (next == null ? "nothing" : next.getProvider().getName()) + " as the export of "
                    + cause.getViolation().getConflicting().get(0).getOwner().getName() + " is discarded, "
                    + cause.getResource().getName() + " needs the package";
        }
        if (next == null) {
            return "Left unwired to avoid an inconsistent " + what(cause) + ", the import is optional";
        }
        return "Replaced by " + next.getProvider().getName() + " to avoid an inconsistent " + what(cause);
    }

    static String what(Fault cause) {
        return cause == null ? "class space" : cause.describe();
    }
}
