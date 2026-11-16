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
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.classspace.ClassSpaceRules;
import org.eclipse.osgi.technology.resolver.conflicts.UsesGraph;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;

/**
 * The class space of the resources of a graph that has its candidates, as built
 * by the {@link ClassSpacePhase}: the rules and indices of the
 * {@link ClassSpaceRules}, the graph of the choices a conflict can depend on and
 * the decisions that follow from the substitution of exports, which change the
 * candidates.
 */
public final class ClassSpace {

    private final ClassSpaceRules rules;
    private final UsesGraph usesGraph;

    ClassSpace(ClassSpaceRules rules, UsesGraph usesGraph) {
        this.rules = rules;
        this.usesGraph = usesGraph;
    }

    /**
     * The static facts about the package imports and exports.
     *
     * @return the uses index
     */
    public UsesIndex getUsesIndex() {
        return rules.getUsesIndex();
    }

    /**
     * The choices a conflict can depend on.
     *
     * @return the uses graph
     */
    public UsesGraph getUsesGraph() {
        return usesGraph;
    }

    /**
     * The exports that their own bundle imports as well.
     *
     * @return the substitution index
     */
    public SubstitutionIndex getSubstitutionIndex() {
        return rules.getSubstitutionIndex();
    }

    /**
     * The class space rule: the first reason the class space of the resource is
     * not consistent, a discarded export it is wired to or a package it would
     * see from different providers. Reads only the state of the selection, so
     * it may be called for several resources in parallel.
     *
     * @param resource the resource to check
     * @return the violation, or empty if the class space is consistent
     */
    public Optional<UsesViolation> violationOf(ResolverResource resource) {
        return rules.violationOf(resource);
    }

    /**
     * Records in the journal of the overlapping imports and in the resources
     * which exports are kept or discarded, after the class space is enforced.
     */
    public void explainSubstitutions() {
        for (Substitution substitution : rules.getSubstitutionIndex().getSubstitutions()) {
            ResolverRequirement overlapping = (ResolverRequirement) substitution.getOverlappingImport();
            ResolverResource owner = overlapping.getOwner();
            if (owner.getState() != ResolveState.RESOLVED) {
                continue;
            }
            ResolverCapability substitute = null;
            for (Candidate candidate : overlapping.getCandidates()) {
                if (candidate.getState() == CandidateState.SELECTED) {
                    substitute = candidate.getCapability() == substitution.getExport() ? null : candidate.getCapability();
                    candidate.select(candidate.getReason() + (substitute == null
                            ? ", the export of the same package of " + owner.getName() + " is kept"
                            : ", the export of the same package of " + owner.getName() + " is discarded"));
                }
            }
            owner.decide((ResolverCapability) substitution.getExport(), substitute);
        }
    }
}
