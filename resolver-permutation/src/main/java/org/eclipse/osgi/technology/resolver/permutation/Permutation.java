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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;

/**
 * A variation of the initial selection: the candidates that are not used, per requirement, the next one of the
 * requirement is used instead.
 * Two permutations are equal if they remove the same candidates, whatever the order and the cause, that is how a
 * permutation is tried only once.
 */
final class Permutation {

    private final Map<ResolverRequirement, Set<Candidate>> delta = new LinkedHashMap<>();
    private final Map<Candidate, Fault> causes = new IdentityHashMap<>();

    Permutation copy() {
        Permutation copy = new Permutation();
        for (Map.Entry<ResolverRequirement, Set<Candidate>> entry : delta.entrySet()) {
            copy.delta.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        copy.causes.putAll(causes);
        return copy;
    }

    Map<ResolverRequirement, Set<Candidate>> getDelta() {
        return delta;
    }

    Set<Candidate> removed(ResolverRequirement requirement) {
        Set<Candidate> removed = delta.get(requirement);
        return removed == null ? Collections.<Candidate> emptySet() : removed;
    }

    /**
     * Does not use the candidate.
     *
     * @param candidate the candidate that is no longer used
     * @param cause     why, may be {@code null}
     */
    void remove(Candidate candidate, Fault cause) {
        Set<Candidate> removed = delta.get(candidate.getRequirement());
        if (removed == null) {
            removed = new LinkedHashSet<>();
            delta.put(candidate.getRequirement(), removed);
        }
        removed.add(candidate);
        causes.put(candidate, cause);
    }

    Fault causeOf(Candidate candidate) {
        return causes.get(candidate);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Permutation && delta.equals(((Permutation) obj).delta);
    }

    @Override
    public int hashCode() {
        return delta.hashCode();
    }
}
