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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;

/**
 * The permutations that are still to be tried, ordered the way the Felix resolver does: the ones that follow from a
 * class space conflict first, then the ones that backtrack an import decision, the ones for substitution last.
 * A permutation that is added while a permutation is checked goes in front of the ones that were added while an
 * earlier one was checked (it is inserted behind the ones of the same check), so the neighbourhood of the last
 * permutation is searched before the older ones.
 */
final class PermutationQueue {

    private final GraphState state;
    private final List<Permutation> uses = new ArrayList<>();
    private final List<Permutation> imports = new ArrayList<>();
    private final List<Permutation> substitutes = new ArrayList<>();
    private int usesIndex;
    private int importIndex;
    private int substituteIndex;
    private final Set<Permutation> processed = new HashSet<>();
    private final Set<ResolverRequirement> mutated = Collections
            .newSetFromMap(new IdentityHashMap<ResolverRequirement, Boolean>());
    // never cleared, once a substitution was permutated more permutations do not help
    private final Set<ResolverRequirement> substituteMutated = Collections
            .newSetFromMap(new IdentityHashMap<ResolverRequirement, Boolean>());
    private long count;

    PermutationQueue(GraphState state) {
        this.state = state;
    }

    /**
     * How many permutations were added so far, it tells if the check of a resource added one.
     */
    long getCount() {
        return count;
    }

    /**
     * Adds a permutation, {@code null} is ignored (it is what {@link GraphState#permutate} returns for none).
     */
    void add(PermutationType type, Permutation permutation) {
        if (permutation == null) {
            return;
        }
        count++;
        switch (type) {
        case USES:
            uses.add(usesIndex++, permutation);
            break;
        case IMPORT:
            imports.add(importIndex++, permutation);
            break;
        default:
            substitutes.add(substituteIndex++, permutation);
            break;
        }
    }

    /**
     * Adds a permutation without the candidate the requirement is wired to if there is another one, once per
     * requirement until the next permutation is taken.
     */
    void permutateIfNeeded(PermutationType type, ResolverRequirement requirement, Permutation base, Fault cause) {
        if (state.remaining(requirement, base) <= 1) {
            return;
        }
        Set<ResolverRequirement> done = type == PermutationType.SUBSTITUTE ? substituteMutated : mutated;
        if (done.add(requirement)) {
            add(type, state.permutate(requirement, base, cause));
        }
    }

    /**
     * @return the next permutation that was not tried yet, {@code null} if there is none
     */
    Permutation next() {
        Permutation next;
        do {
            if (!uses.isEmpty()) {
                next = uses.remove(0);
            } else if (!imports.isEmpty()) {
                next = imports.remove(0);
            } else if (!substitutes.isEmpty()) {
                next = substitutes.remove(0);
            } else {
                return null;
            }
        } while (!processed.add(next));
        // what is added from now on goes to the front
        usesIndex = 0;
        importIndex = 0;
        substituteIndex = 0;
        mutated.clear();
        return next;
    }
}
