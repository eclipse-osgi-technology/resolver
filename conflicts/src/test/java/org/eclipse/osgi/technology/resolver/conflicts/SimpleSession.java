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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.Checkpoint;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.classspace.SubstitutionRule;
import org.eclipse.osgi.technology.resolver.classspace.UsesRule;
import org.eclipse.osgi.technology.resolver.index.CandidateSource;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.resource.Requirement;

/**
 * The smallest session a strategy can work on: the indices and rules built
 * from a candidate graph, the failure of a resource is recorded at the
 * resource only and nothing depends on another resource.
 */
final class SimpleSession implements UsesSession {

    private final List<ResolverResource> resources;
    private final UsesIndex usesIndex;
    private final UsesGraph usesGraph;
    private final SubstitutionIndex substitutionIndex;
    private final UsesRule usesRule;
    private final SubstitutionRule substitutionRule;

    SimpleSession(List<ResolverResource> resources, CandidateSource candidates) {
        this.resources = resources;
        this.usesIndex = new UsesIndex(resources, candidates, r -> true);
        this.usesGraph = new UsesGraph(usesIndex, candidates);
        this.substitutionIndex = new SubstitutionIndex(resources, candidates, r -> true, resources);
        this.usesRule = new UsesRule(usesIndex);
        this.substitutionRule = new SubstitutionRule(substitutionIndex);
    }

    @Override
    public Checkpoint checkpoint() {
        throw new UnsupportedOperationException("The strategies of the tests do not try solutions");
    }

    @Override
    public Collection<ResolverResource> getResources() {
        return resources;
    }

    @Override
    public List<ResolverResource> getMandatory() {
        return resources;
    }

    @Override
    public List<ResolverResource> getOptional() {
        return Collections.emptyList();
    }

    @Override
    public UsesIndex getUsesIndex() {
        return usesIndex;
    }

    @Override
    public UsesGraph getUsesGraph() {
        return usesGraph;
    }

    @Override
    public SubstitutionIndex getSubstitutionIndex() {
        return substitutionIndex;
    }

    @Override
    public Optional<UsesViolation> violationOf(ResolverResource resource) {
        Optional<UsesViolation> hidden = substitutionRule.check(resource);
        return hidden.isPresent() ? hidden : usesRule.check(resource);
    }

    @Override
    public void fail(ResolverResource resource, UsesViolation violation) {
        resource.markFailed(violation);
    }

    @Override
    public Collection<ResolverRequirement> propagateFailures() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ResolverResource> affected(Collection<ResolverRequirement> changed) {
        List<ResolverResource> result = new ArrayList<>();
        for (Requirement requirement : usesGraph.affected(changed)) {
            ResolverResource owner = ((ResolverRequirement) requirement).getOwner();
            if (!result.contains(owner)) {
                result.add(owner);
            }
        }
        return result;
    }
}
