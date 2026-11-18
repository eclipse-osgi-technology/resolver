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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * Decides which resources can be resolved at all and what each of them selects,
 * without looking at the class space.
 * <ol>
 * <li>Local checks (parallel): look at one resource only and change only that
 * resource.</li>
 * <li>Cross resource checks (parallel rounds): look at the state of the
 * providers and return verdicts, that are applied between the rounds, until
 * nothing changes. All resources start out as resolvable, so cycles resolve and
 * a resource only fails if it really lacks a provider.</li>
 * <li>Selection (parallel, local): choose the candidate to wire and record
 * why.</li>
 * </ol>
 */
public final class ResolvabilityPhase implements ResolverPhase<Void> {

    private final ResolverGraph graph;

    /**
     * @param graph the graph to check, with all its candidates
     */
    public ResolvabilityPhase(ResolverGraph graph) {
        this.graph = graph;
    }

    /**
     * Fails the resources that have no way to be resolved, marks all others as
     * resolved, an optimistic resolution the class space enforcement may still
     * take back, and selects the candidates to wire.
     */
    @Override
    public Void run() {
        check();
        select();
        return null;
    }

    private void check() {
        graph.getResources().parallelStream().filter(r -> r.getState() == ResolveState.PENDING)
                .forEach(ResolvabilityPhase::localCheck);
        crossResourceChecks();
    }

    /**
     * Local check: a mandatory requirement without any candidate can never be
     * satisfied.
     */
    private static void localCheck(ResolverResource resource) {
        for (ResolverRequirement requirement : resource.getResolverRequirements()) {
            if (requirement.isEffective() && !requirement.isOptional() && requirement.getCandidates().isEmpty()) {
                resource.markFailed(requirement);
                return;
            }
        }
    }

    private void crossResourceChecks() {
        List<ResolverRequirement> failures;
        do {
            List<ResolverResource> pending = graph.getResources().stream()
                    .filter(r -> r.getState() == ResolveState.PENDING).collect(Collectors.toList());
            // verdicts are computed against the state of the previous round only, so the outcome does not depend on timing
            failures = pending.parallelStream().map(ResolvabilityPhase::unsatisfied).collect(Collectors.toList());
            for (int i = 0; i < pending.size(); i++) {
                if (failures.get(i) != null) {
                    pending.get(i).markFailed(failures.get(i));
                }
            }
        } while (failures.stream().anyMatch(f -> f != null));
        for (ResolverResource resource : graph.getResources()) {
            if (resource.getState() == ResolveState.PENDING) {
                resource.markResolved();
            }
        }
    }

    /**
     * The first mandatory requirement that has no candidate left whose provider
     * is still resolvable.
     */
    private static ResolverRequirement unsatisfied(ResolverResource resource) {
        for (ResolverRequirement requirement : resource.getResolverRequirements()) {
            if (requirement.isEffective() && !requirement.isOptional() && !hasUsableCandidate(requirement)) {
                return requirement;
            }
        }
        return null;
    }

    private static boolean hasUsableCandidate(ResolverRequirement requirement) {
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.isIncluded() && candidate.getFailedResource() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records what became of the candidates of every requirement, in parallel
     * as it is local to a resource.
     */
    private void select() {
        graph.getResources().parallelStream().forEach(ResolvabilityPhase::select);
    }

    /**
     * Local: records what became of the candidates of the requirements of one
     * resource.
     */
    private static void select(ResolverResource resource) {
        boolean resolved = resource.getState() == ResolveState.RESOLVED;
        for (ResolverRequirement requirement : resource.getResolverRequirements()) {
            boolean selected = false;
            // a package can only be imported from one exporter, so multiple does not apply there
            boolean multiple = requirement.isMultiple()
                    && !PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace());
            for (Candidate candidate : requirement.getCandidates()) {
                if (!candidate.isIncluded()) {
                    continue;
                }
                ResolverResource failed = candidate.getFailedResource();
                if (failed != null) {
                    candidate.reject(cannotBeResolved(failed));
                } else if (resolved && !selected) {
                    candidate.select(multiple ? "Selected as the requirement has cardinality multiple"
                            : "Selected as it is the first candidate that can be resolved (highest priority)");
                    selected = !multiple;
                } else if (resolved) {
                    candidate.defer("Not considered because a candidate with higher priority is selected");
                }
            }
        }
    }

    /**
     * The reason a candidate is rejected because its provider failed.
     *
     * @param failed the provider that can not be resolved
     * @return the reason to record
     */
    static String cannotBeResolved(ResolverResource failed) {
        ResolverRequirement requirement = failed.getFailedRequirement();
        return "Provider " + failed.getName() + " can not be resolved"
                + (requirement == null ? "" : ", it has no usable provider for " + requirement);
    }
}
