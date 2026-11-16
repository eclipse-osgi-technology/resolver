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

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.classspace.ClassSpaceRules;
import org.eclipse.osgi.technology.resolver.conflicts.UsesGraph;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;

/**
 * Builds the {@link ClassSpace} of a graph: the indices, the rules and the
 * graph of the choices a conflict can depend on.
 */
public final class ClassSpacePhase {

    private final ResolverGraph graph;

    /**
     * @param graph the graph with its candidates, the fragments have to be
     *            handled already
     */
    public ClassSpacePhase(ResolverGraph graph) {
        this.graph = graph;
    }

    /**
     * Builds the indices and rules. Substitution decides which exports exist,
     * so it is looked at before anything is selected: what an export is needed
     * for is known from the candidates of all requirements. An export that a
     * resource that has to be resolved can not do without is not discarded: the
     * other providers of the import of the same package are rejected, so no
     * choice is made that has to be taken back.
     *
     * @return the class space of the graph
     */
    public ClassSpace run() {
        ClassSpaceRules rules = new ClassSpaceRules(graph.getResources(), graph.getMandatory());
        keepNeededExports(rules.getSubstitutionIndex());
        return new ClassSpace(rules, new UsesGraph(rules.getUsesIndex(), rules.getCandidates()));
    }

    private static void keepNeededExports(SubstitutionIndex substitutionIndex) {
        for (Substitution substitution : substitutionIndex.getSubstitutions()) {
            if (!substitution.isPinned()) {
                continue;
            }
            ResolverRequirement overlapping = (ResolverRequirement) substitution.getOverlappingImport();
            ResolverRequirement needing = (ResolverRequirement) substitution.getPinnedBy();
            for (Candidate candidate : overlapping.getCandidates()) {
                if (candidate.getCapability() != substitution.getExport() && candidate.isIncluded()) {
                    candidate.reject("Not used as the export of " + overlapping.getOwner().getName() + " is needed by "
                            + needing.getOwner().getName() + ", that has no other provider");
                }
            }
        }
    }
}
