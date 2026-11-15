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

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * The rule for substitution: a bundle that exports and imports the same package
 * has its export only if the import is wired to that export, wired to another
 * provider the export is discarded (Core specification 3.8.1, "Importing and
 * Exporting the Same Package"). A resource must not be wired to an export that
 * is discarded. The check only reads the graph, it does not change it.
 */
public final class SubstitutionRule {

    private final SubstitutionIndex index;

    /**
     * Creates a rule for the exports an index knows can be discarded.
     *
     * @param index the exports that can be discarded, built from the same
     *     resources as the graph
     */
    public SubstitutionRule(SubstitutionIndex index) {
        this.index = index;
    }

    /**
     * Checks the imports of a resource for the providers that are currently
     * selected.
     *
     * @param resource the resource whose imports are checked
     * @return the first import of the resource that is wired to a discarded
     * export, empty if there is none
     */
    public Optional<UsesViolation> check(ResolverResource resource) {
        if (index.getSubstitutions().isEmpty()) {
            return Optional.empty();
        }
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                continue;
            }
            for (Candidate candidate : requirement.getCandidates()) {
                if (candidate.getState() != CandidateState.SELECTED) {
                    continue;
                }
                Substitution substitution = index.getSubstitution(candidate.getCapability());
                if (substitution == null || substitution.getOverlappingImport() == requirement) {
                    continue;
                }
                Optional<ResolverCapability> substitute = substituteOf(substitution);
                if (substitute.isPresent()) {
                    return Optional.of(new UsesViolation(
                            String.valueOf(candidate.getCapability().getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)),
                            Arrays.asList(candidate.getCapability(), substitute.get()), requirement));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tells if an export is kept or discarded for the providers that are
     * currently selected.
     *
     * @param substitution the export together with the import of the same
     *     package
     * @return the provider the exporter uses instead of its own export, empty
     * if the export is kept or if the import is not part of a candidate graph
     */
    private static Optional<ResolverCapability> substituteOf(Substitution substitution) {
        if (!(substitution.getOverlappingImport() instanceof ResolverRequirement)) {
            return Optional.empty();
        }
        ResolverRequirement overlapping = (ResolverRequirement) substitution.getOverlappingImport();
        for (Candidate candidate : overlapping.getCandidates()) {
            if (candidate.getState() == CandidateState.SELECTED) {
                return candidate.getCapability() == substitution.getExport() ? Optional.empty()
                        : Optional.of(candidate.getCapability());
            }
        }
        return Optional.empty();
    }
}
