/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.conflicts;

import java.util.Collections;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.osgi.resource.Requirement;

/**
 * A strategy that tries the alternatives of the choices behind a conflict, one
 * at a time: a change is kept if everything that can see it is consistent,
 * otherwise it is taken back with a checkpoint. A resource whose conflict can not
 * be decided fails.
 */
final class SwapStrategy implements UsesConflictStrategy {

    @Override
    public void enforceUses(UsesSession session) {
        for (ResolverResource resource : session.getResources()) {
            Optional<UsesViolation> violation = session.violationOf(resource);
            if (resource.getState() == ResolveState.RESOLVED && violation.isPresent() && !decide(session, resource)) {
                session.fail(resource, violation.get());
            }
        }
    }

    private boolean decide(UsesSession session, ResolverResource resource) {
        for (ResolverRequirement own : resource.getResolverRequirements()) {
            for (Requirement choice : session.getUsesGraph().reachable(own)) {
                if (swap(session, resource, (ResolverRequirement) choice)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Selects another candidate for the requirement if that makes the resource
     * and everything affected consistent.
     */
    private boolean swap(UsesSession session, ResolverResource resource, ResolverRequirement requirement) {
        Candidate selected = null;
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.getState() == CandidateState.SELECTED) {
                selected = candidate;
            }
        }
        if (selected == null) {
            return false;
        }
        for (Candidate alternative : requirement.getCandidates()) {
            if (alternative.isAlternative()) {
                try (Checkpoint attempt = session.checkpoint()) {
                    selected.defer("Replaced by " + alternative.getProvider().getName());
                    alternative.select("Selected to avoid a conflict");
                    if (!session.violationOf(resource).isPresent() && consistent(session, requirement)) {
                        return true;
                    }
                    attempt.rollback();
                }
            }
        }
        return false;
    }

    private boolean consistent(UsesSession session, ResolverRequirement changed) {
        for (ResolverResource affected : session.affected(Collections.singletonList(changed))) {
            if (affected.getState() == ResolveState.RESOLVED && session.violationOf(affected).isPresent()) {
                return false;
            }
        }
        return true;
    }
}
