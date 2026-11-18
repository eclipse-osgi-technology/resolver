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
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.ResolveContext;

/**
 * Builds the graph: the resources the resolve context asks for and everything
 * reachable from their effective requirements through the providers the context
 * knows. Single threaded, the only phase that asks the context for providers.
 */
public final class DiscoveryPhase implements ResolverPhase<Void> {

    private final ResolveContext context;
    private final ResolverGraph graph;

    /**
     * @param context the resolve context to ask for resources and providers
     * @param graph   the graph to fill
     */
    public DiscoveryPhase(ResolveContext context, ResolverGraph graph) {
        this.context = context;
        this.graph = graph;
    }

    /**
     * Fills the graph, every requirement gets the candidates the context offers
     * for it.
     */
    @Override
    public Void run() {
        Deque<ResolverResource> queue = new ArrayDeque<>();
        for (Resource resource : context.getMandatoryResources()) {
            graph.addMandatory(resourceOf(resource, queue));
        }
        for (Resource resource : context.getOptionalResources()) {
            graph.addOptional(resourceOf(resource, queue));
        }
        while (!queue.isEmpty()) {
            ResolverResource resource = queue.poll();
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                requirement.setEffective(context.isEffective(requirement.unwrap()));
                if (!requirement.isEffective()) {
                    continue;
                }
                List<Capability> providers = findProviders(requirement.unwrap());
                for (Capability provider : providers) {
                    ResolverResource owner = resourceOf(provider.getResource(), queue);
                    requirement.addCandidate(owner.capability(provider));
                }
            }
        }
        return null;
    }

    /**
     * The providers the resolve context knows for the requirement. The
     * capabilities of a fragment have the identity of the host, not the one of
     * the fragment the context sees, so if the requirement asks for the
     * identity of a bundle the fragments are asked for as well and the
     * capabilities are matched again once they are hosted.
     */
    private List<Capability> findProviders(Requirement requirement) {
        List<Capability> providers = context.findProviders(requirement);
        if (providers == null) {
            providers = new ArrayList<>();
        }
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())
                || !HostIdentityFilter.isIdentityRequested(filter)) {
            return providers;
        }
        Set<Capability> exact = Collections.newSetFromMap(new IdentityHashMap<>());
        exact.addAll(providers);
        List<Capability> relaxed = context.findProviders(new RelaxedRequirement(requirement,
                HostIdentityFilter.relax(filter, requirement.getNamespace())));
        if (relaxed == null) {
            return providers;
        }
        List<Capability> result = new ArrayList<>();
        for (Capability capability : relaxed) {
            if (exact.contains(capability) || !capability.getResource().getRequirements(HostNamespace.HOST_NAMESPACE).isEmpty()) {
                result.add(capability);
            }
        }
        for (Capability capability : providers) {
            if (!result.contains(capability)) {
                result.add(capability);
            }
        }
        return result;
    }

    private ResolverResource resourceOf(Resource resource, Deque<ResolverResource> queue) {
        ResolverResource wrapper = graph.get(resource);
        if (wrapper == null) {
            wrapper = new ResolverResource(resource, graph.getPackageViewCache());
            graph.add(wrapper);
            queue.add(wrapper);
        }
        return wrapper;
    }
}
