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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;

/**
 * What the specification asks for fragments, in the three places a resolve
 * needs it: before anything is selected which fragments are attached to a host
 * and where their capabilities are provided, and after the selection which
 * fragments a host ends up with. Every step runs single threaded as it relates
 * the candidates of several resources.
 */
public final class FragmentAttachmentPhase {

    private final ResolveContext context;
    private final ResolverGraph graph;

    /**
     * @param context the resolve context that decides where a hosted capability
     *       is placed
     * @param graph   the graph to change
     */
    public FragmentAttachmentPhase(ResolveContext context, ResolverGraph graph) {
        this.context = context;
        this.graph = graph;
    }

    /**
     * The steps that run before anything is selected: chooses the fragments
     * that are attached to a host and places their capabilities. The selected
     * fragments are attached after the selection by
     * {@link #attachSelectedFragments()}.
     */
    public void run() {
        selectFragments();
        hostCapabilities();
    }

    /**
     * Only one fragment of a given symbolic name is attached to a host, the one
     * with the highest version (OSGi Core, "Fragment Bundles": a host
     * attaches a single fragment out of those that have the same symbolic
     * name). The host candidate of the others is rejected, a fragment without
     * any host left can not be resolved.
     */
    private void selectFragments() {
        Map<ResolverResource, Map<String, List<Candidate>>> byHost = new LinkedHashMap<>();
        for (ResolverResource resource : graph.getResources()) {
            if (!resource.isFragment()) {
                continue;
            }
            if (!hasProviderForEveryPayloadRequirement(resource)) {
                continue;
            }
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                if (requirement.isEffective() && HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                    for (Candidate candidate : requirement.getCandidates()) {
                        byHost.computeIfAbsent(candidate.getProvider(), k -> new LinkedHashMap<>())
                                .computeIfAbsent(String.valueOf(resource.getIdentity()), k -> new ArrayList<>())
                                .add(candidate);
                    }
                }
            }
        }
        for (Map<String, List<Candidate>> byName : byHost.values()) {
            for (List<Candidate> candidates : byName.values()) {
                candidates.sort((a, b) -> b.getRequirement().getOwner().getVersion()
                        .compareTo(a.getRequirement().getOwner().getVersion()));
                ResolverResource attached = candidates.get(0).getRequirement().getOwner();
                for (Candidate candidate : candidates.subList(1, candidates.size())) {
                    candidate.reject("Not attached because fragment " + attached.getName()
                            + " with the same symbolic name and a higher version is attached to the host "
                            + candidate.getProvider().getName());
                }
            }
        }
    }

    /**
     * A fragment that can not be resolved anyway does not keep a lower version
     * of itself from being attached, only fragments that can be resolved take
     * part in the selection of the highest version.
     */
    private static boolean hasProviderForEveryPayloadRequirement(ResolverResource fragment) {
        for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
            if (requirement.isEffective() && !requirement.isOptional() && !isNotPayload(requirement)
                    && requirement.getCandidates().stream().noneMatch(Candidate::isIncluded)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells if a requirement of a fragment is one of the fragment itself, the
     * host and the execution environment, and not of the payload that is wired
     * for the host.
     *
     * @param requirement a requirement of a fragment
     * @return {@code true} if the requirement is not wired for the host
     */
    public static boolean isNotPayload(ResolverRequirement requirement) {
        return HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())
                || ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE.equals(requirement.getNamespace());
    }

    /**
     * Capabilities of a fragment are provided by each host the fragment may be
     * attached to. As required by the specification the resolve context decides
     * where such a hosted capability is placed in the candidates of a
     * requirement ({@link ResolveContext#insertHostedCapability}), the declared
     * capability of the fragment is replaced.
     */
    private void hostCapabilities() {
        for (ResolverResource resource : graph.getResources()) {
            for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                if (requirement.isEffective()) {
                    hostCapabilities(requirement);
                }
            }
        }
    }

    private void hostCapabilities(ResolverRequirement requirement) {
        // mirrors the candidates as the resolve context sees them
        List<Capability> offered = new ArrayList<>();
        for (Candidate candidate : requirement.getCandidates()) {
            offered.add(candidate.getCapability().unwrap());
        }
        List<Candidate> candidates = new ArrayList<>(requirement.getCandidates());
        for (Candidate candidate : candidates) {
            ResolverResource fragment = candidate.getProvider();
            if (!fragment.isFragment()
                    || IdentityNamespace.IDENTITY_NAMESPACE.equals(candidate.getCapability().getNamespace())) {
                continue;
            }
            int index = requirement.getCandidates().indexOf(candidate);
            offered.remove(index);
            requirement.removeCandidate(index);
            boolean hosted = false;
            for (ResolverRequirement hostRequirement : fragment.getResolverRequirements()) {
                if (!HostNamespace.HOST_NAMESPACE.equals(hostRequirement.getNamespace())) {
                    continue;
                }
                for (Candidate hostCandidate : hostRequirement.getCandidates()) {
                    if (hostCandidate.isIncluded()) {
                        ResolverResource host = hostCandidate.getProvider();
                        Capability declared = candidate.getCapability().unwrap();
                        HostedCapability capability = new FragmentHostedCapability(host.unwrap(), declared,
                                PackageNamespace.PACKAGE_NAMESPACE.equals(declared.getNamespace())
                                        ? HostIdentityFilter.hostedAttributes(declared.getAttributes(),
                                                host.getIdentity(), host.getVersion())
                                        : declared.getAttributes());
                        int position = context.insertHostedCapability(offered, capability);
                        Candidate hostedCandidate = requirement.insertCandidate(position,
                                host.hostedCapability(capability, fragment), "Provided by host " + host.getName()
                                        + ", fragment " + fragment.getName() + " may be attached to it");
                        String filter = requirement.unwrap().getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
                        if (HostIdentityFilter.isIdentityRequested(filter)
                                && !HostIdentityFilter.matches(filter, capability.getAttributes())) {
                            hostedCandidate.reject("The identity of the exporting bundle is the one of the host "
                                    + host.getName() + ", not the one of the fragment " + fragment.getName());
                        }
                        hosted = true;
                    }
                }
            }
            if (!hosted) {
                requirement.insertCandidate(index, candidate.getCapability(), "Offered by the resolve context")
                        .reject("Not provided as fragment " + fragment.getName() + " is not attached to any host");
                offered.add(index, candidate.getCapability().unwrap());
            }
        }
    }

    /**
     * After the selection: a fragment contributes its imports to the class
     * space of the hosts it is attached to.
     */
    public void attachSelectedFragments() {
        Map<ResolverResource, List<ResolverResource>> byHost = new IdentityHashMap<>();
        for (ResolverResource fragment : graph.getResources()) {
            if (fragment.isFragment() && fragment.getState() == ResolveState.RESOLVED) {
                for (ResolverResource host : fragment.getHosts()) {
                    byHost.computeIfAbsent(host, k -> new ArrayList<>()).add(fragment);
                }
            }
        }
        byHost.forEach(ResolverResource::setAttachedFragments);
    }
}
