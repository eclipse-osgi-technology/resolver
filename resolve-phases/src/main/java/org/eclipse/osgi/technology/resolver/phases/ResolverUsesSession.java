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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesGraph;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.resource.Requirement;

/**
 * The {@link UsesSession} of a resolve: what a strategy that decides the class
 * space conflicts sees of the graph and the class space, and the operations it
 * may use.
 */
public final class ResolverUsesSession implements UsesSession {

    private final ResolverGraph graph;
    private final ClassSpace classSpace;

    /**
     * @param graph      the graph after the selection
     * @param classSpace the class space of the graph
     */
    public ResolverUsesSession(ResolverGraph graph, ClassSpace classSpace) {
        this.graph = graph;
        this.classSpace = classSpace;
    }

    @Override
    public Collection<ResolverResource> getResources() {
        return graph.getResources();
    }

    @Override
    public List<ResolverResource> getMandatory() {
        return graph.getMandatory();
    }

    @Override
    public List<ResolverResource> getOptional() {
        return graph.getOptional();
    }

    @Override
    public UsesIndex getUsesIndex() {
        return classSpace.getUsesIndex();
    }

    @Override
    public UsesGraph getUsesGraph() {
        return classSpace.getUsesGraph();
    }

    @Override
    public SubstitutionIndex getSubstitutionIndex() {
        return classSpace.getSubstitutionIndex();
    }

    /**
     * The first reason the class space of the resource is not consistent: a
     * discarded export or a package seen twice.
     */
    @Override
    public Optional<UsesViolation> violationOf(ResolverResource resource) {
        return classSpace.violationOf(resource);
    }

    @Override
    public void fail(ResolverResource resource, UsesViolation violation) {
        violation.getHiddenImport().ifPresent(hidden -> {
            for (Candidate candidate : hidden.getCandidates()) {
                if (candidate.getState() == CandidateState.SELECTED) {
                    candidate.reject("Not usable, " + violation);
                }
            }
        });
        resource.markFailed(violation);
    }

    /**
     * The owners of the changed requirements and of everything that can see
     * them, a fragment counts for its hosts. In a stable order (the one the
     * graph finds them in), so a strategy that handles them one by one behaves
     * the same for the same input.
     */
    @Override
    public Collection<ResolverResource> affected(Collection<ResolverRequirement> changed) {
        Set<ResolverResource> owners = new LinkedHashSet<>();
        for (Requirement requirement : classSpace.getUsesGraph().affected(changed)) {
            addWithHosts(owners, ((ResolverRequirement) requirement).getOwner());
        }
        // whoever could be wired to an export sees it disappear if the import of its exporter changes
        for (ResolverRequirement requirement : changed) {
            Substitution substitution = classSpace.getSubstitutionIndex().getSubstitutionOfImport(requirement);
            if (substitution != null) {
                for (Requirement demanded : substitution.getDemand()) {
                    addWithHosts(owners, ((ResolverRequirement) demanded).getOwner());
                }
            }
        }
        return owners;
    }

    private static void addWithHosts(Set<ResolverResource> owners, ResolverResource owner) {
        owners.add(owner);
        if (owner.isFragment()) {
            owners.addAll(owner.getHosts());
        }
    }

    /**
     * A selected provider that failed afterwards is no longer wired, a
     * mandatory requirement without one fails.
     *
     * @return the requirements that lost a selected provider
     */
    @Override
    public Collection<ResolverRequirement> propagateFailures() {
        Set<ResolverRequirement> touched = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (ResolverResource resource : graph.getResources()) {
                if (resource.getState() != ResolveState.RESOLVED) {
                    continue;
                }
                for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                    boolean rejected = false;
                    boolean usable = false;
                    for (Candidate candidate : requirement.getCandidates()) {
                        if (candidate.getState() == CandidateState.SELECTED) {
                            ResolverResource failed = candidate.getFailedResource();
                            if (failed == null) {
                                usable = true;
                            } else {
                                candidate.reject(ResolvabilityPhase.cannotBeResolved(failed));
                                rejected = true;
                                touched.add(requirement);
                            }
                        }
                    }
                    if (rejected && !usable && !requirement.isOptional()) {
                        resource.markFailed(requirement);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        return touched;
    }
}
