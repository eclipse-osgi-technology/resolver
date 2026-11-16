/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.classspace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.CandidateSource;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The rules for the class space of the resources of a candidate graph together
 * with the indices they read, built from the resources of the graph.
 * The substitution rule is asked first, as a resource that is wired to a
 * discarded export is inconsistent regardless of what it uses. Like the single
 * rules this only reads the graph, it never changes it.
 */
public final class ClassSpaceRules {

    private final SubstitutionIndex substitutionIndex;
    private final UsesIndex usesIndex;
    private final CandidateSource candidates;
    private final SubstitutionRule substitutionRule;
    private final UsesRule usesRule;

    /**
     * Builds the indices and the rules over the candidates of the resources.
     * The substitution index looks only at candidates that are still included,
     * the uses index at all of them, as it is built once and has to hold for
     * every selection.
     *
     * @param resources the resources that form the candidate graph, each with
     *            its candidates
     * @param mandatory the resources that have to be resolved, an export of
     *            one of them that it needs is not discarded, a fragment does
     *            not count as it is not known to be attached
     */
    public ClassSpaceRules(Collection<? extends ResolverResource> resources,
            Collection<? extends ResolverResource> mandatory) {
        this.candidates = requirement -> candidatesOf(requirement, false);
        CandidateSource included = requirement -> candidatesOf(requirement, true);
        List<ResolverResource> certain = new ArrayList<>();
        for (ResolverResource resource : mandatory) {
            if (!resource.isFragment()) {
                certain.add(resource);
            }
        }
        this.substitutionIndex = new SubstitutionIndex(resources, included, resource -> true, certain);
        this.usesIndex = new UsesIndex(resources, candidates, resource -> true, fragmentsOf(resources)::get);
        this.substitutionRule = new SubstitutionRule(substitutionIndex);
        this.usesRule = new UsesRule(usesIndex);
    }

    private static List<Capability> candidatesOf(Requirement requirement, boolean onlyIncluded) {
        if (!(requirement instanceof ResolverRequirement)) {
            return null;
        }
        List<Capability> all = new ArrayList<>();
        for (Candidate candidate : ((ResolverRequirement) requirement).getCandidates()) {
            if (!onlyIncluded || candidate.isIncluded()) {
                all.add(candidate.getCapability());
            }
        }
        return all;
    }

    /**
     * The fragments that can be attached to a host, by host.
     */
    private static Map<Resource, List<Resource>> fragmentsOf(Collection<? extends ResolverResource> resources) {
        Map<Resource, List<Resource>> fragments = new IdentityHashMap<>();
        for (ResolverResource fragment : resources) {
            if (!fragment.isFragment()) {
                continue;
            }
            for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
                if (HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                    for (Candidate candidate : requirement.getCandidates()) {
                        if (candidate.isIncluded()) {
                            fragments.computeIfAbsent(candidate.getProvider(), k -> new ArrayList<>()).add(fragment);
                        }
                    }
                }
            }
        }
        return fragments;
    }

    /**
     * The static facts about the package imports and exports.
     *
     * @return the uses index
     */
    public UsesIndex getUsesIndex() {
        return usesIndex;
    }

    /**
     * The exports that their own bundle imports as well.
     *
     * @return the substitution index
     */
    public SubstitutionIndex getSubstitutionIndex() {
        return substitutionIndex;
    }

    /**
     * The capabilities that can satisfy a requirement of the graph, all of them
     * regardless of the state of the candidate, for indices and graphs that are
     * built once.
     *
     * @return the candidates of a requirement
     */
    public CandidateSource getCandidates() {
        return candidates;
    }

    /**
     * Applies both rules to the providers that are currently selected.
     * Reads only the state of the selection, so it may be called for several
     * resources in parallel.
     *
     * @param resource the resource to check
     * @return the first discarded export the resource is wired to or package
     *     it would see from different providers, empty if its class space is
     *     consistent
     */
    public Optional<UsesViolation> violationOf(ResolverResource resource) {
        Optional<UsesViolation> hidden = substitutionRule.check(resource);
        return hidden.isPresent() ? hidden : usesRule.check(resource);
    }
}
