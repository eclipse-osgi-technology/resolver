/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.phases;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

/**
 * Turns the selection recorded in the graph into the wires a resolver returns.
 */
public final class WiringPhase {

    private final ResolverGraph graph;

    /**
     * @param graph the graph after the class space is enforced
     */
    public WiringPhase(ResolverGraph graph) {
        this.graph = graph;
    }

    /**
     * Only resolved resources contribute wires, and only those needed for the
     * requested ones. As a fragment contributes to the class loader of its host
     * (OSGi Core, "Fragment Bundles"), it is wired to its host through the host
     * namespace, the requirements of its payload are wired on behalf of the
     * host while the wire keeps the requirement declared by the fragment, and
     * the capabilities of a fragment are provided by the host. Only the
     * execution environment requirements of a fragment are wired to the
     * fragment itself.
     *
     * @return the wires of the resources that are part of the result
     */
    public Map<Resource, List<Wire>> run() {
        Set<ResolverResource> reachable = reachable();
        Map<Resource, List<Wire>> result = new LinkedHashMap<>();
        for (ResolverResource resource : reachable) {
            List<Wire> wires = new ArrayList<>();
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                if (!resource.isFragment() || FragmentAttachmentPhase.isNotPayload(requirement)) {
                    addWires(wires, resource.unwrap(), requirement);
                }
            }
            if (!resource.isFragment()) {
                for (ResolverResource fragment : reachable) {
                    if (fragment.getHosts().contains(resource)) {
                        for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
                            if (!FragmentAttachmentPhase.isNotPayload(requirement)) {
                                addWires(wires, resource.unwrap(), requirement);
                            }
                        }
                    }
                }
            }
            result.put(resource.unwrap(), wires);
        }
        return result;
    }

    private Set<ResolverResource> reachable() {
        Set<ResolverResource> reachable = new LinkedHashSet<>();
        Deque<ResolverResource> queue = new ArrayDeque<>();
        queue.addAll(graph.getMandatory());
        queue.addAll(graph.getOptional());
        while (!queue.isEmpty()) {
            ResolverResource resource = queue.poll();
            if (resource.getState() != ResolveState.RESOLVED || !reachable.add(resource)) {
                continue;
            }
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                for (Candidate candidate : requirement.getCandidates()) {
                    if (candidate.getState() == CandidateState.SELECTED) {
                        queue.add(candidate.getProvider());
                        queue.add(candidate.getCapability().getDeclaringResource());
                    }
                }
            }
        }
        return reachable;
    }

    private static void addWires(List<Wire> wires, Resource requirer, ResolverRequirement requirement) {
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.getState() == CandidateState.SELECTED) {
                ResolverCapability capability = candidate.getCapability();
                if (capability.getOwner().unwrap() == requirer
                        && PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                    // the export is used, there is no wire for the import that matches it
                    continue;
                }
                wires.add(new ResolvedWire(requirer, requirement.unwrap(), capability.getOwner().unwrap(),
                        capability.unwrap()));
            }
        }
    }
}
