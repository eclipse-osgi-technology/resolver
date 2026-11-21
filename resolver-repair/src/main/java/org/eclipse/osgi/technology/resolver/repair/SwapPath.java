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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;

/**
 * The changes the search made on the way to the conflict it looks at, in the order they were made.
 * A path is only a record, the changes themselves are taken back with the
 * {@link org.eclipse.osgi.technology.resolver.candidates.Checkpoint} of the attempt that made them.
 */
final class SwapPath {

    private final List<CandidateSwap> swaps = new ArrayList<>();

    void add(CandidateSwap swap) {
        swaps.add(swap);
    }

    int size() {
        return swaps.size();
    }

    boolean isEmpty() {
        return swaps.isEmpty();
    }

    /**
     * Forgets the changes made after the mark, a value of {@link #size()}.
     */
    void truncate(int mark) {
        swaps.subList(mark, swaps.size()).clear();
    }

    /**
     * True if the requirement was changed on this path, a requirement is changed once per path.
     */
    boolean contains(ResolverRequirement requirement) {
        for (CandidateSwap swap : swaps) {
            if (swap.getRequirement() == requirement) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many optional imports the changes leave unwired.
     */
    int unwired() {
        int count = 0;
        for (CandidateSwap swap : swaps) {
            if (swap.getAlternative() == null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The candidates the changes end with, a set of changes is the same whatever the order they were made in.
     */
    Set<Candidate> alternatives() {
        Set<Candidate> result = new HashSet<>();
        for (CandidateSwap swap : swaps) {
            result.add(swap.getAlternative() == null ? swap.getDropped() : swap.getAlternative());
        }
        return result;
    }

    /**
     * The requirements changed after the mark, a value of {@link #size()}.
     */
    List<ResolverRequirement> requirementsSince(int mark) {
        List<ResolverRequirement> result = new ArrayList<>();
        for (CandidateSwap swap : swaps.subList(mark, swaps.size())) {
            result.add(swap.getRequirement());
        }
        return result;
    }
}
