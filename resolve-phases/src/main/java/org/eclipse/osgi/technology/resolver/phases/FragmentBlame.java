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

import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.osgi.service.resolver.ResolutionException;

/**
 * Finds the fragments to blame for a class space inconsistency. A fragment
 * shapes the class space of its host in two ways, it provides hosted
 * capabilities (declared by the fragment, owned by the host) and it contributes
 * its own requirements. A capability or a requirement that is part of a
 * violation and belongs to a fragment names the fragment and the host that
 * is inconsistent with it. A fragment that was asked for explicitly is never
 * blamed, a resolve with it fails.
 */
final class FragmentBlame {

    private final ResolverGraph graph;
    private final ExcludedFragments excluded;
    private boolean found;
    private ResolutionException cause;

    private FragmentBlame(ResolverGraph graph, ExcludedFragments excluded) {
        this.graph = graph;
        this.excluded = excluded;
    }

    /**
     * Adds the fragments that are to blame for a failed resource to the
     * excluded ones.
     *
     * @param graph the graph after the conflicts were decided
     * @param excluded the fragments that are not attached
     * @return {@code true} if a fragment was added, the resolve has to run
     *     again without it
     */
    static boolean blame(ResolverGraph graph, ExcludedFragments excluded) {
        FragmentBlame blame = new FragmentBlame(graph, excluded);
        for (ResolverResource resource : graph.getResources()) {
            if (resource.getState() == ResolveState.FAILED && resource.getUsesViolation() != null) {
                blame.blame(resource, resource.getUsesViolation());
            }
        }
        return blame.found;
    }

    private void blame(ResolverResource resource, UsesViolation violation) {
        cause = FailurePhase.exceptionFor(resource);
        String reason = "Not attached to the inconsistent " + resource.getName() + ", " + violation;
        for (ResolverCapability capability : violation.getConflicting()) {
            blame(resource, capability, Optional.empty(), reason);
        }
        // every requirement of an attached fragment that is wired to one of the capabilities, not only the first one,
        // the fragment may have failed with the host and no longer count for its class space
        for (ResolverResource fragment : resource.getAttachedFragments()) {
            for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
                for (Candidate candidate : requirement.getCandidates()) {
                    if (candidate.getState() == CandidateState.SELECTED
                            && violation.getConflicting().contains(candidate.getCapability())) {
                        blame(fragment, resource, reason);
                    }
                }
            }
        }
        for (UsesChain chain : violation.getChains()) {
            for (Optional<UsesChain> link = Optional.of(chain); link.isPresent(); link = link.get().getParent()) {
                blame(link.get().getViewer(), link.get().getCapability(), link.get().getRequirement(), reason);
            }
        }
        violation.getHiddenImport().ifPresent(hidden -> blame(resource, hidden, reason));
    }

    private void blame(ResolverResource viewer, ResolverCapability capability, Optional<ResolverRequirement> requirement,
            String reason) {
        ResolverResource declaring = capability.getDeclaringResource();
        if (declaring != capability.getOwner() && declaring.isFragment()) {
            blame(declaring, capability.getOwner(), reason);
        }
        requirement.ifPresent(seen -> blame(viewer, seen, reason));
    }

    private void blame(ResolverResource viewer, ResolverRequirement requirement, String reason) {
        if (requirement.getOwner().isFragment()) {
            blame(requirement.getOwner(), viewer, reason);
        }
    }

    private void blame(ResolverResource fragment, ResolverResource host, String reason) {
        if (graph.getMandatory().contains(fragment)) {
            excluded.setConflict(fragment.unwrap(), host.unwrap(), cause);
        } else if (excluded.add(fragment.unwrap(), host.unwrap(), reason)) {
            excluded.setCause(fragment.unwrap(), cause);
            found = true;
        }
    }
}
