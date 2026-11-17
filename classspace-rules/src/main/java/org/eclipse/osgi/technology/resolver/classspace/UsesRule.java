/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.classspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;

/**
 * The rule for the {@code uses} directive: the class space of a resource must
 * be consistent. If a resource sees a package from a capability that uses
 * another package, it must see the used package from the very same provider as
 * the owner of the capability does. This holds transitively, the used package
 * of a used package is covered as well. A package that has only one capability
 * in the whole resolve can not be inconsistent, the {@link UsesIndex} tells
 * which packages are contested so everything else is not looked at. The check
 * only reads the graph, it does not change it.
 */
public final class UsesRule {

    private final UsesIndex index;

    /**
     * Creates a rule for the packages an index marks as contested.
     *
     * @param index tells which packages can be inconsistent at all, built from
     *     the same resources as the graph
     */
    public UsesRule(UsesIndex index) {
        this.index = index;
    }
    /**
     * Checks the class space of a resource for the providers that are currently
     * selected.
     *
     * @param resource the resource whose class space is checked
     * @return the first inconsistency in the view of the resource, empty if it
     * is consistent
     */
    public Optional<UsesViolation> check(ResolverResource resource) {
        List<ResolverCapability> twice = resource.getConflictingImports();
        if (!twice.isEmpty()) {
            return Optional.of(new UsesViolation(
                    String.valueOf(twice.get(0).getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)), twice,
                    Optional.empty(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        }
        Map<String, Map<List<ResolverCapability>, List<UsesChain>>> sources = new LinkedHashMap<>();
        Map<Capability, Boolean> visited = new LinkedHashMap<>();
        for (ResolverCapability capability : resource.getSeenCapabilities()) {
            collect(resource, capability, null, sources, visited);
        }
        for (Map.Entry<String, Map<List<ResolverCapability>, List<UsesChain>>> entry : sources.entrySet()) {
            List<ResolverCapability> seen = resource.getPackageSources(entry.getKey());
            // implied packages only constrain what the resource imports,
            // exports or requires itself, they are no imports
            if (seen.isEmpty()) {
                continue;
            }
            for (Map.Entry<List<ResolverCapability>, List<UsesChain>> expected : entry.getValue().entrySet()) {
                List<ResolverCapability> implied = expected.getKey();
                if (!compatible(seen, implied)) {
                    Map<Capability, ResolverCapability> all = new LinkedHashMap<>();
                    for (ResolverCapability capability : implied) {
                        all.put(capability.getDeclared(), capability);
                    }
                    for (ResolverCapability capability : seen) {
                        all.put(capability.getDeclared(), capability);
                    }
                    return Optional.of(new UsesViolation(entry.getKey(), new ArrayList<>(all.values()), Optional.empty(), seen, implied,
                        expected.getValue()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * What a resource sees of a package and what another provider of that
     * package implies must overlap completely in one direction: a package split
     * over required bundles is seen partly by a consumer of one of them, that
     * is fine as long as one side contains the other.
     *
     * @param seen the capabilities the resource sees the package from
     * @param implied the capabilities another provider implies for the package
     * @return true if one side contains the other
     */
    private static boolean compatible(List<ResolverCapability> seen, List<ResolverCapability> implied) {
        Map<ResolverResource, Boolean> seenFrom = new IdentityHashMap<>();
        for (ResolverCapability capability : seen) {
            seenFrom.put(capability.getOwner(), Boolean.TRUE);
        }
        Map<ResolverResource, Boolean> impliedFrom = new IdentityHashMap<>();
        for (ResolverCapability capability : implied) {
            impliedFrom.put(capability.getOwner(), Boolean.TRUE);
        }
        return seenFrom.keySet().containsAll(impliedFrom.keySet()) || impliedFrom.keySet().containsAll(seenFrom.keySet());
    }

    private void collect(ResolverResource viewer, ResolverCapability capability, UsesChain parent,
            Map<String, Map<List<ResolverCapability>, List<UsesChain>>> sources,
            Map<Capability, Boolean> visited) {
        if (visited.put(capability.getDeclared(), Boolean.TRUE) != null) {
            return;
        }
        UsesChain chain = new UsesChain(viewer, capability, parent);
        ResolverResource owner = capability.getOwner();
        // a chain of uses that never reaches a package with more than one
        // possible provider is consistent whatever is selected
        if (!index.reachesContested(capability.getUses())) {
            return;
        }
        List<String> uses = capability.getUses();
        for (String name : uses) {
            if (!index.reachesContested(name)) {
                continue;
            }
            // several sources if the owner gets the package from required
            // bundles, they are one class space
            List<ResolverCapability> owned = owner.getPackageSources(name);
            if (owned.isEmpty()) {
                continue;
            }
            if (index.isContested(name)) {
                sources.computeIfAbsent(name, k -> new LinkedHashMap<>())
                        .computeIfAbsent(owned, k -> new ArrayList<>()).add(chain);
            }
            for (ResolverCapability source : owned) {
                collect(owner, source, chain, sources, visited);
            }
        }
    }
}
