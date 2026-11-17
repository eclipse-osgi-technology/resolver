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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.failures.ExportSubstitutedException;
import org.eclipse.osgi.technology.resolver.failures.UnsatisfiedRequirementException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.service.resolver.ResolutionException;

/**
 * Tells why a resource could not be resolved, with the exception that names the
 * root cause.
 */
public final class FailurePhase {

    private FailurePhase() {
    }

    /**
     * The exception that tells why the resource can not be resolved, following
     * the failing providers to the cause.
     *
     * @param resource a resource that failed
     * @return the exception for the root cause of the failure
     */
    public static ResolutionException exceptionFor(ResolverResource resource) {
        Set<ResolverResource> visited = new HashSet<>();
        while (visited.add(resource)) {
            if (resource.getUsesViolation() != null) {
                UsesViolation violation = resource.getUsesViolation();
                List<Capability> conflicting = new ArrayList<>();
                for (ResolverCapability capability : violation.getConflicting()) {
                    conflicting.add(capability.getDeclared());
                }
                Optional<ResolverRequirement> hidden = violation.getHiddenImport();
                if (hidden.isPresent()) {
                    return new ExportSubstitutedException(hidden.get().unwrap(), conflicting.get(0), conflicting.get(1));
                }
                return new UsesConstraintViolationException(violation.getPackageName(), resource.unwrap(), conflicting,
                        choices(violation));
            }
            ResolverRequirement requirement = resource.getFailedRequirement();
            ResolverResource next = null;
            for (Candidate candidate : requirement.getCandidates()) {
                ResolverResource failed = candidate.getFailedResource();
                if (failed != null && (failed.getFailedRequirement() != null || failed.getUsesViolation() != null)) {
                    next = failed;
                    break;
                }
            }
            if (next == null) {
                return new UnsatisfiedRequirementException(requirement.unwrap());
            }
            resource = next;
        }
        return new UnsatisfiedRequirementException(resource.getFailedRequirement().unwrap());
    }

    /**
     * The requirements on the chains of the violation, each one a place where
     * another provider would change what the resource sees.
     */
    private static List<Requirement> choices(UsesViolation violation) {
        Set<Requirement> choices = new LinkedHashSet<>();
        for (UsesChain chain : violation.getChains()) {
            for (Optional<UsesChain> link = Optional.of(chain); link.isPresent(); link = link.get().getParent()) {
                link.get().getRequirement().ifPresent(requirement -> choices.add(requirement.unwrap()));
            }
        }
        return new ArrayList<>(choices);
    }
}
