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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;

/**
 * Depth first search over the changes that make the class space of one resource consistent, with a fixed number of
 * optional imports it may leave unwired.
 * An optional import is treated like a mandatory one: it is only left unwired as the last choice of an owner, when no
 * other provider removes the conflict, and then only if it contributes to it, see {@link Choices#droppable}.
 * The number is the depth of one search, {@link RepairSearch} repeats the search with a growing one so that the repair
 * that takes the least away from the resources is found first.
 * The first inconsistent resource of the pending ones is repaired by one change of its choice points, then everything
 * that can see the change is looked at, and so on until nothing is inconsistent.
 * What a try changed is taken back with a checkpoint if it does not lead to a consistent state.
 * <p>
 * The owner in the methods of this class is the resource whose conflict is being repaired right now, which is the
 * root of the search or a resource reached later on the path.
 */
final class ConflictSearch {

    private final RepairState state;
    private final ResolverResource root;
    private final UsesViolation rootViolation;
    private final SwapPath path;
    private final int depth;

    /**
     * The sets of changes that were tried, for this depth only: a set that failed can succeed with a larger one.
     */
    private final Set<Set<Candidate>> tried = new HashSet<>();

    ConflictSearch(RepairState state, ResolverResource root, UsesViolation rootViolation, SwapPath path, int depth) {
        this.state = state;
        this.root = root;
        this.rootViolation = rootViolation;
        this.path = path;
        this.depth = depth;
    }

    /**
     * @return true if the root is consistent and so is everything that can see the changes, the path holds them
     */
    boolean run() {
        return repair(Collections.singletonList(root));
    }

    private boolean repair(List<ResolverResource> pending) {
        ResolverResource owner = null;
        UsesViolation violation = null;
        for (ResolverResource next : pending) {
            if (next.getState() != ResolveState.RESOLVED || state.isSacrificed(next)) {
                continue;
            }
            // what was inconsistent before is not made worse by a change and has its own turn, it does not veto the change
            if (next != root && state.inconsistent.contains(next)) {
                continue;
            }
            UsesViolation found = path.isEmpty() && next == root ? rootViolation : state.session.violationOf(next).orElse(null);
            if (found != null) {
                owner = next;
                violation = found;
                break;
            }
        }
        if (owner == null) {
            return true;
        }
        return repairOwner(owner, violation, pending);
    }

    private boolean repairOwner(ResolverResource owner, UsesViolation violation, List<ResolverResource> pending) {
        // giving up a resource that was not asked for can still cut the chain
        if (!state.isSacrificing() && Choices.irreducible(violation, owner)) {
            return false;
        }
        // the same move of an owner from one provider to another is tried once, whichever of its imports it starts with
        Set<List<ResolverResource>> moved = new HashSet<>();
        for (ResolverRequirement requirement : Choices.choicePoints(state, owner, violation)) {
            Candidate selected = Choices.selectedOf(requirement);
            if (selected == null || path.contains(requirement)) {
                continue;
            }
            for (Candidate alternative : requirement.getCandidates()) {
                if (!alternative.isAlternative() || !moved.add(Arrays.asList(requirement.getOwner(),
                        selected.getProvider(), alternative.getProvider()))) {
                    continue;
                }
                if (attempt(owner, pending, p -> CandidateSwap.move(state, p, requirement, selected, alternative, owner,
                        violation))) {
                    return true;
                }
            }
        }
        // an optional import may stay unwired, that is the last choice as the owner then loses what it asked for
        List<ResolverRequirement> droppable = Choices.droppable(state, owner, violation);
        if (path.unwired() >= depth) {
            state.cutOff |= !droppable.isEmpty();
            droppable = Collections.emptyList();
        }
        for (ResolverRequirement requirement : droppable) {
            if (path.contains(requirement)) {
                continue;
            }
            Candidate selected = Choices.selectedOf(requirement);
            if (attempt(owner, pending, p -> p.add(CandidateSwap.drop(requirement, selected, owner)))) {
                return true;
            }
        }
        // a resource that was not asked for explicitly is given up instead, if the caller looks for a sacrifice
        if (state.isSacrificing() && owner != root && !state.isMandatory(owner)) {
            state.sacrifice(owner);
            return repair(pending);
        }
        return false;
    }

    /**
     * Makes a change and goes on with everything that can see it, what was broken is looked at first: a resource that
     * is left without a choice ends the attempt at once instead of after every other option of the owner was tried
     * (fail first). Everything is taken back if that does not lead to a consistent state.
     */
    private boolean attempt(ResolverResource owner, List<ResolverResource> pending, Consumer<SwapPath> change) {
        int mark = path.size();
        int sacrificed = state.sacrificeMark();
        try (Checkpoint attempt = state.session.checkpoint()) {
            change.accept(path);
            if (tried.add(path.alternatives())) {
                Set<ResolverResource> following = new LinkedHashSet<>(state.session.affected(path.requirementsSince(mark)));
                following.add(owner);
                following.addAll(pending);
                if (repair(new ArrayList<>(following))) {
                    return true;
                }
            }
            state.releaseSacrifices(sacrificed);
            attempt.rollback();
            path.truncate(mark);
        }
        return false;
    }
}
