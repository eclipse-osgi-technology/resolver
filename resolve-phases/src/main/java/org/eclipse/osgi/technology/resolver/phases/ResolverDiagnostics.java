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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.diagnostics.Diagnostics;
import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * Explains a resolve operation by reading the state recorded in the graph, so
 * the explanation can never deviate from what a resolver actually decided.
 */
class ResolverDiagnostics implements Diagnostics {

    private final Map<Resource, ResolverResource> resources;

    ResolverDiagnostics(Map<Resource, ResolverResource> resources) {
        this.resources = resources;
    }

    @Override
    public Optional<String> getMessage(Resource resource) {
        ResolverResource node = find(resource);
        if (node == null) {
            return Optional.empty();
        }
        switch (node.getState()) {
        case RESOLVED:
            for (ResolverRequirement requirement : node.getResolverRequirements()) {
                if (requirement.isEffective() && requirement.isOptional() && !hasSelected(requirement)) {
                    return Optional.of("Some optional requirements are not resolved" + attachment(node));
                }
            }
            return Optional.of("All requirements resolved" + attachment(node));
        case FAILED:
            if (node.getUsesViolation() != null) {
                return Optional.of("Not resolved, " + node.getUsesViolation());
            }
            ResolverRequirement failed = node.getFailedRequirement();
            if (failed.getCandidates().isEmpty()) {
                return Optional.of("Not resolved, there is no provider for requirement " + failed);
            }
            List<String> reasons = new ArrayList<>();
            for (Candidate candidate : failed.getCandidates()) {
                reasons.add(candidate.toString());
            }
            return Optional.of("Not resolved, none of the " + failed.getCandidates().size()
                    + " providers of requirement " + failed + " can be used: " + String.join("; ", reasons));
        default:
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getMessage(Requirement requirement) {
        ResolverResource node = find(requirement.getResource());
        ResolverRequirement wrapper = node == null ? null : node.requirement(requirement);
        if (wrapper == null || !wrapper.isEffective()) {
            return Optional.empty();
        }
        if (wrapper.getCandidates().isEmpty()) {
            return Optional.of("There is no provider" + (wrapper.isOptional() ? " (optional requirement)" : ""));
        }
        List<String> lines = new ArrayList<>();
        for (Candidate candidate : wrapper.getCandidates()) {
            lines.add(candidate.toString());
        }
        return Optional.of(String.join("; ", lines));
    }

    @Override
    public Optional<String> getMessage(Capability capability) {
        ResolverResource node = find(capability.getResource());
        if (node == null) {
            return Optional.empty();
        }
        for (ResolverCapability wrapper : node.getResolverCapabilities()) {
            if (wrapper != capability && wrapper.unwrap() != capability || !node.isSubstitutable(wrapper)) {
                continue;
            }
            ResolverCapability substitute = node.getSubstitute(wrapper);
            String name = String.valueOf(wrapper.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
            return Optional.of(substitute == null
                    ? "Kept, " + node.getName() + " imports package " + name + " as well and is wired to its own export"
                    : "Discarded, " + node.getName() + " imports package " + name + " as well and is wired to the export of "
                            + substitute.getOwner().getName() + " instead");
        }
        return Optional.empty();
    }

    private static String attachment(ResolverResource node) {
        List<String> hosts = new ArrayList<>();
        for (ResolverResource host : node.isFragment() ? node.getHosts() : new ArrayList<ResolverResource>()) {
            hosts.add(host.getName());
        }
        return hosts.isEmpty() ? "" : ", attached to host " + String.join(", ", hosts);
    }

    private static boolean hasSelected(ResolverRequirement requirement) {
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.getState() == CandidateState.SELECTED) {
                return true;
            }
        }
        return false;
    }

    private ResolverResource find(Resource resource) {
        if (resource instanceof ResolverResource) {
            return (ResolverResource) resource;
        }
        return resources.get(resource);
    }
}
