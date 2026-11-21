/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Requirement;

/**
 * Which choices the search may change for the conflict of a resource, and in which order it tries them.
 * The uses graph tells which requirements can influence a class space, the order decides which repair is found first.
 */
final class Choices {

    private Choices() {
    }

    /**
     * The package imports and required bundles that can influence the class space of the resource.
     * The ones behind its imports come first: a provider adapting to its consumer is preferred over the consumer
     * giving up the provider it prefers, as the consumer's requirement is what the provider has to serve.
     * The ones behind its imports that a kept repair of this run already changed are left alone, see
     * {@link RepairState#settled}.
     */
    static List<ResolverRequirement> choicePoints(RepairState state, ResolverResource resource, UsesViolation violation) {
        List<ResolverRequirement> own = new ArrayList<>();
        Set<Requirement> ownSet = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            if (isChoice(requirement)) {
                own.add(requirement);
                ownSet.add(requirement);
            }
        }
        List<ResolverRequirement> starts = new ArrayList<>(own);
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            if (!isChoice(requirement) && !state.session.getUsesGraph().dependencies(requirement).isEmpty()) {
                starts.add(requirement);
            }
        }
        List<ResolverRequirement> result = new ArrayList<>();
        Set<Requirement> behind = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ResolverRequirement requirement : starts) {
            for (Requirement reachable : state.session.getUsesGraph().reachable(requirement)) {
                if (!ownSet.contains(reachable) && behind.add(reachable) && isChoice((ResolverRequirement) reachable)
                        && !state.settled.contains(reachable)) {
                    result.add((ResolverRequirement) reachable);
                }
            }
        }
        result.addAll(own);
        return withSubstitution(state, violation, involved(state, violation, result));
    }

    /**
     * The choices that can see the violated package come first, in their order: a change of any other choice can not
     * remove the conflict, it only has a use if a later change relies on it.
     */
    private static List<ResolverRequirement> involved(RepairState state, UsesViolation violation, List<ResolverRequirement> choices) {
        List<ResolverRequirement> involved = new ArrayList<>();
        List<ResolverRequirement> others = new ArrayList<>();
        for (ResolverRequirement requirement : choices) {
            (sees(state, requirement, violation.getPackageName()) ? involved : others).add(requirement);
        }
        involved.addAll(others);
        return involved;
    }

    /**
     * True if no choice can remove the conflict: what the resource sees of the package can not change, and one of the
     * chains that imply a different provider can not be cut or redirected either, as every requirement on it, and the
     * ones that wire the provider it implies, are mandatory with a single candidate.
     * Whatever is tried then, the conflict stays, so the optional imports are not worth a search.
     * A view that is not wired through a requirement (an own export) is not known to be fixed.
     * The search does not use this shortcut while it looks for a sacrifice, as giving up a resource that nobody asked
     * for can still cut a chain that is fixed otherwise.
     */
    static boolean irreducible(UsesViolation violation, ResolverResource resource) {
        if (violation.getHiddenImport().isPresent() || violation.getSeen().isEmpty() || violation.getChains().isEmpty()) {
            return false;
        }
        for (ResolverCapability seen : violation.getSeen()) {
            if (!fixed(new UsesChain(resource, seen, null))) {
                return false;
            }
        }
        for (UsesChain chain : violation.getChains()) {
            if (fixedChain(chain, violation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fixedChain(UsesChain chain, UsesViolation violation) {
        for (Optional<UsesChain> link = Optional.of(chain); link.isPresent(); link = link.get().getParent()) {
            if (!fixed(link.get())) {
                return false;
            }
        }
        for (ResolverCapability implied : violation.getImplied()) {
            if (!fixed(new UsesChain(chain.getCapability().getOwner(), implied, null))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the requirement a view goes through is known, has no other candidate and may not be left unwired.
     * What a required bundle provides depends on its own imports as well (a substituted export), so such a view
     * is not known to be fixed.
     */
    private static boolean fixed(UsesChain link) {
        ResolverRequirement requirement = link.getRequirement().orElse(null);
        if (requirement == null || !PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())
                || !isChoice(requirement) || requirement.isOptional()) {
            return false;
        }
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.isAlternative()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The optional imports the resource may leave unwired to get rid of the conflict: only its own, as what another
     * resource asked for is not taken away from it for the sake of this one, and only those that contribute to the
     * conflict (the import of the violated package, or a requirement on a chain of the violation that tells why the
     * resource sees a capability using it), as leaving any other import unwired never removes the conflict.
     * An optional import is treated like a mandatory one and only given up if the owner can not be consistent with it.
     * The optional imports behind the resource are not considered, which keeps the search small and leaves the
     * providers of a resource unchanged for the sake of their consumers.
     */
    static List<ResolverRequirement> droppable(RepairState state, ResolverResource resource, UsesViolation violation) {
        String name = violation.getPackageName();
        Set<Requirement> onChains = Collections.newSetFromMap(new IdentityHashMap<>());
        for (UsesChain chain : violation.getChains()) {
            for (Optional<UsesChain> link = Optional.of(chain); link.isPresent(); link = link.get().getParent()) {
                link.get().getRequirement().ifPresent(onChains::add);
            }
        }
        List<ResolverRequirement> result = new ArrayList<>();
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            if (isChoice(requirement) && requirement.isOptional() && selectedOf(requirement) != null
                    && (onChains.contains(requirement) || importsPackage(requirement, name))
                    && sees(state, requirement, name)) {
                result.add(requirement);
            }
        }
        return result;
    }

    private static boolean importsPackage(ResolverRequirement requirement, String name) {
        for (Candidate candidate : requirement.getCandidates()) {
            ResolverCapability capability = candidate.getCapability();
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())
                    && name.equals(String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if a candidate of the requirement, or of an import it reaches through uses, provides or uses the package.
     */
    private static boolean sees(RepairState state, ResolverRequirement requirement, String name) {
        return state.seen.computeIfAbsent(requirement, r -> new HashMap<>()).computeIfAbsent(name,
                n -> reaches(state, requirement, n));
    }

    private static boolean reaches(RepairState state, ResolverRequirement requirement, String name) {
        for (Requirement reachable : state.session.getUsesGraph().reachable(requirement)) {
            for (Candidate candidate : ((ResolverRequirement) reachable).getCandidates()) {
                ResolverCapability capability = candidate.getCapability();
                if (capability.getUses().contains(name) || (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())
                        && name.equals(String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE))))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A discarded export is repaired by the consumer using another provider or by the exporter keeping the export:
     * the two imports involved come first.
     */
    private static List<ResolverRequirement> withSubstitution(RepairState state, UsesViolation violation,
            List<ResolverRequirement> choices) {
        Substitution substitution = substitutionOf(state, violation);
        if (substitution == null) {
            return choices;
        }
        List<ResolverRequirement> result = new ArrayList<>();
        for (ResolverRequirement requirement : new ResolverRequirement[] { violation.getHiddenImport().orElse(null),
                (ResolverRequirement) substitution.getOverlappingImport() }) {
            if (isChoice(requirement)) {
                result.add(requirement);
            }
        }
        for (ResolverRequirement requirement : choices) {
            if (!result.contains(requirement)) {
                result.add(requirement);
            }
        }
        return result;
    }

    /**
     * The substitution behind a discarded export the violation is about, {@code null} for any other violation.
     */
    static Substitution substitutionOf(RepairState state, UsesViolation violation) {
        return !violation.getHiddenImport().isPresent() ? null
                : state.session.getSubstitutionIndex().getSubstitution(violation.getConflicting().get(0));
    }

    static boolean isChoice(ResolverRequirement requirement) {
        String namespace = requirement.getNamespace();
        return (PackageNamespace.PACKAGE_NAMESPACE.equals(namespace) || BundleNamespace.BUNDLE_NAMESPACE.equals(namespace))
                && !requirement.isMultiple()
                && requirement.isEffective()
                && requirement.getOwner().getState() == ResolveState.RESOLVED;
    }

    static Candidate selectedOf(ResolverRequirement requirement) {
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.getState() == CandidateState.SELECTED) {
                return candidate;
            }
        }
        return null;
    }
}
