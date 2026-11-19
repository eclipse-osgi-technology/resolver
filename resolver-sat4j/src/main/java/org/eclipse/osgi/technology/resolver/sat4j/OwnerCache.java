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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;

/**
 * Groups the literals of capabilities by the resource that provides them. A
 * provider with several capabilities gets a literal of its own that is true if
 * and only if one of them is, so the encoding can state a constraint per
 * provider. The result is kept for every group of capabilities, as the same
 * group is asked for by every resource that sees it.
 */
final class OwnerCache {

    private final PseudoBooleanProblem problem;
    private final Map<Map<ResolverCapability, Integer>, Map<ResolverResource, Integer>> cache = new IdentityHashMap<>();

    OwnerCache(PseudoBooleanProblem problem) {
        this.problem = problem;
    }

    /**
     * The literal per provider: the one of its capability if there is just
     * one, else a literal that is true if and only if one of them is.
     *
     * @param capabilities the capabilities with the literal that says one is
     *            seen
     * @return the literal per providing resource
     */
    Map<ResolverResource, Integer> owners(Map<ResolverCapability, Integer> capabilities) {
        Map<ResolverResource, Integer> cached = cache.get(capabilities);
        if (cached != null) {
            return cached;
        }
        Map<ResolverResource, List<Integer>> byOwner = new IdentityHashMap<>();
        for (Map.Entry<ResolverCapability, Integer> entry : capabilities.entrySet()) {
            byOwner.computeIfAbsent(entry.getKey().getOwner(), k -> new ArrayList<Integer>()).add(entry.getValue());
        }
        Map<ResolverResource, Integer> result = new IdentityHashMap<>();
        for (Map.Entry<ResolverResource, List<Integer>> entry : byOwner.entrySet()) {
            List<Integer> literals = entry.getValue();
            if (literals.size() == 1) {
                result.put(entry.getKey(), literals.get(0));
            } else {
                // the owner is seen if and only if one of its capabilities is, as it is used positively as well
                int owner = problem.newVariable();
                int[] any = new int[literals.size() + 1];
                any[0] = -owner;
                for (int i = 0; i < literals.size(); i++) {
                    problem.clause(-literals.get(i), owner);
                    any[i + 1] = literals.get(i);
                }
                problem.clause(any);
                result.put(entry.getKey(), owner);
            }
        }
        cache.put(capabilities, result);
        return result;
    }
}
