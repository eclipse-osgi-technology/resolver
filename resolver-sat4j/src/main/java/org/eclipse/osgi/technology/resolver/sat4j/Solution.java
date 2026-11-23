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

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.resource.Requirement;

/**
 * The decisions of the solver, applied to the graph with the reasons the diagnostics show.
 */
final class Solution {

    private final UsesSolver problem;
    private final boolean[] truth;
    private final Map<ResolverResource, UsesViolation> violations;
    private final Map<Requirement, ResolverResource> blamed = new IdentityHashMap<>();
    private final Map<ResolverResource, ResolverResource> blamedProviders = new IdentityHashMap<>();

    Solution(UsesSolver problem, boolean[] truth, Map<ResolverResource, UsesViolation> violations) {
        this.problem = problem;
        this.truth = truth;
        this.violations = violations;
        for (Map.Entry<ResolverResource, UsesViolation> entry : violations.entrySet()) {
            ResolverResource resource = entry.getKey();
            for (ResolverCapability capability : entry.getValue().getConflicting()) {
                blamedProviders.putIfAbsent(capability.getOwner(), resource);
            }
            for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
                blamed.putIfAbsent(requirement, resource);
                for (Requirement reachable : problem.graph.reachable(requirement)) {
                    blamed.putIfAbsent(reachable, resource);
                }
            }
        }
    }

    boolean isResolved(ResolverResource resource) {
        Integer variable = problem.resolved.get(resource);
        return variable == null || truth[variable];
    }

    /**
     * Changes the selection of the graph to the solution and records why.
     *
     * @param changed receives the requirements whose selection changed
     * @return the resources the solver gave up, they have to fail with their violation
     */
    List<ResolverResource> apply(List<ResolverRequirement> changed) {
        for (Map.Entry<ResolverRequirement, List<Candidate>> entry : problem.usable.entrySet()) {
            ResolverRequirement requirement = entry.getKey();
            if (HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                // the attachment of a fragment is not a choice of the problem
                continue;
            } else if (!isResolved(requirement.getOwner())) {
                continue;
            } else if (requirement.isMultiple()) {
                applyMultiple(requirement, entry.getValue(), changed);
            } else {
                applySingle(requirement, entry.getValue(), changed);
            }
        }
        List<ResolverResource> given = new ArrayList<>();
        for (ResolverResource resource : problem.live) {
            if (!isResolved(resource)) {
                given.add(resource);
            }
        }
        return given;
    }

    private void applyMultiple(ResolverRequirement requirement, List<Candidate> candidates,
            List<ResolverRequirement> changed) {
        for (Candidate candidate : candidates) {
            boolean wired = truth[problem.wires.get(candidate)];
            if (candidate.getState() == CandidateState.SELECTED && !wired) {
                candidate.defer("Not wired by the constraint solver" + cause(requirement, candidate));
                changed.add(requirement);
            } else if (candidate.getState() != CandidateState.SELECTED && wired) {
                candidate.select("Selected by the constraint solver");
                changed.add(requirement);
            }
        }
    }

    private void applySingle(ResolverRequirement requirement, List<Candidate> candidates,
            List<ResolverRequirement> changed) {
        Candidate selected = null;
        Candidate chosen = null;
        for (Candidate candidate : candidates) {
            if (candidate.getState() == CandidateState.SELECTED) {
                selected = candidate;
            }
            if (truth[problem.wires.get(candidate)]) {
                chosen = candidate;
            }
        }
        if (selected == chosen) {
            return;
        }
        changed.add(requirement);
        if (chosen != null) {
            String cause = cause(requirement, selected);
            if (selected != null) {
                selected.defer("Replaced by " + chosen.getProvider().getName() + " by the constraint solver" + cause);
            }
            chosen.select(selected == null ? "Selected by the constraint solver" + cause
                    : "Selected by the constraint solver instead of " + selected.getProvider().getName() + cause);
        } else if (selected != null) {
            selected.defer("Left unwired by the constraint solver" + cause(requirement, selected)
                    + ", the requirement is optional");
        }
    }

    /**
     * Relates a change to the conflict it was made for, through the uses graph or the provider that was in conflict.
     */
    private String cause(ResolverRequirement requirement, Candidate dropped) {
        ResolverResource conflicting = blamed.get(requirement);
        if (conflicting == null && dropped != null) {
            conflicting = blamedProviders.get(dropped.getProvider());
        }
        if (conflicting == null) {
            return " so that every class space stays consistent";
        }
        UsesViolation violation = violations.get(conflicting);
        return ", the choice takes part in the " + (violation.getHiddenImport().isPresent() ? "discarded export of package "
                : "uses constraint violation on package ") + violation.getPackageName() + " of " + conflicting.getName();
    }
}
