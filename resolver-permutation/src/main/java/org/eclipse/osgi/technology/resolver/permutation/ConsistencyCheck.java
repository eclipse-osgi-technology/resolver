/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.permutation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.resource.Requirement;

/**
 * Checks the class space of every resource for the selection one {@link Permutation} makes, and adds the
 * permutations that follow from what is found to the {@link PermutationQueue}.
 * The permutations are the ones the Felix resolver makes: for a conflict between what a resource sees and what the
 * providers it sees imply, the requirement of every blame is changed from the resource towards the provider and
 * from the provider towards the resource (one requirement of a chain each), and the import of the resource itself is
 * backtracked.
 */
final class ConsistencyCheck {

    private final UsesSession session;
    private final GraphState state;
    private final PermutationQueue queue;
    private final Permutation permutation;
    private final Set<ResolverResource> consistent = Collections
            .newSetFromMap(new IdentityHashMap<ResolverResource, Boolean>());
    private final Map<ResolverRequirement, Set<Candidate>> multiple = new IdentityHashMap<>();
    private final Map<ResolverResource, Fault> faulty = new LinkedHashMap<>();
    private Fault unresolvable;

    /**
     * @param permutation the permutation to check, it is not changed, the changes the check makes to the selection
     *                    are kept in a copy that is available by {@link #getSelection()}
     */
    ConsistencyCheck(UsesSession session, GraphState state, PermutationQueue queue, Permutation permutation) {
        this.session = session;
        this.state = state;
        this.queue = queue;
        this.permutation = permutation.copy();
    }

    /**
     * The permutation with the changes the check had to make to the selection.
     */
    Permutation getSelection() {
        return permutation;
    }

    /**
     * The candidates of requirements that take any provider that are not used.
     */
    Map<ResolverRequirement, Set<Candidate>> getMultiple() {
        return multiple;
    }

    /**
     * The resources that are inconsistent, by the root they were found from.
     */
    Map<ResolverResource, Fault> getFaulty() {
        return faulty;
    }

    /**
     * The fault that stopped the check without being a conflict of a class space, a requirement that lost its last provider.
     */
    Fault getUnresolvable() {
        return unresolvable;
    }

    /**
     * @return true if the class space of every resource is consistent
     */
    boolean run() {
        state.restore();
        state.apply(permutation, false);
        if (!discardSubstituted()) {
            return false;
        }
        // the selection is final from here on
        List<ResolverResource> roots = new ArrayList<>(session.getMandatory());
        roots.addAll(session.getOptional());
        for (ResolverResource root : roots) {
            if (root.getState() != ResolveState.RESOLVED) {
                continue;
            }
            Fault fault = null;
            if (root.isFragment()) {
                // a fragment has no class space on its own, the one of its hosts is checked
                for (ResolverResource host : root.getHosts()) {
                    fault = check(host);
                    if (fault != null) {
                        break;
                    }
                }
            } else {
                fault = check(root);
            }
            if (fault != null) {
                faulty.put(root, fault);
            }
        }
        return faulty.isEmpty();
    }

    /**
     * Gets the imports that are wired to an export that is substituted by an import of its own resource to the next
     * candidate, and permutates the substituting imports.
     *
     * @return false if an import has no candidate left
     */
    private boolean discardSubstituted() {
        Set<ResolverResource> consumers = Collections
                .newSetFromMap(new IdentityHashMap<ResolverResource, Boolean>());
        List<ResolverResource> ordered = new ArrayList<>();
        for (Substitution substitution : session.getSubstitutionIndex().getSubstitutions()) {
            if (substitution.getOverlappingImport() instanceof ResolverRequirement) {
                queue.permutateIfNeeded(PermutationType.SUBSTITUTE,
                        (ResolverRequirement) substitution.getOverlappingImport(), permutation, null);
            }
            for (Requirement demand : substitution.getDemand()) {
                if (demand instanceof ResolverRequirement) {
                    ResolverResource owner = ((ResolverRequirement) demand).getOwner();
                    if (consumers.add(owner)) {
                        ordered.add(owner);
                    }
                }
            }
        }
        for (ResolverResource consumer : ordered) {
            if (consumer.getState() != ResolveState.RESOLVED) {
                continue;
            }
            while (true) {
                Optional<UsesViolation> violation = session.violationOf(consumer);
                if (!violation.isPresent() || !violation.get().getHiddenImport().isPresent()) {
                    break;
                }
                ResolverRequirement hidden = violation.get().getHiddenImport().get();
                Candidate current = state.current(hidden, permutation);
                if (current == null) {
                    break;
                }
                Fault fault = new Fault(consumer, violation.get());
                if (state.remaining(hidden, permutation) <= 1 && !hidden.isOptional()) {
                    unresolvable = fault;
                    return false;
                }
                permutation.remove(current, fault);
                state.apply(hidden, permutation, false);
            }
        }
        return true;
    }

    /**
     * Checks the resource and everything it depends on for the first provider of every requirement.
     */
    private Fault check(ResolverResource resource) {
        if (consistent.contains(resource)) {
            return null;
        }
        Optional<UsesViolation> violation = session.violationOf(resource);
        if (violation.isPresent()) {
            Fault fault = new Fault(resource, violation.get());
            if (violation.get().getHiddenImport().isPresent()) {
                return fault;
            }
            if (!resource.getConflictingImports().isEmpty()) {
                return permutateImports(resource, violation.get(), fault);
            }
            if (!permutateUses(resource, violation.get(), fault)) {
                return fault;
            }
            // every blame of the conflict was dismissed by a requirement that takes any provider
        }
        // a resource is consistent while it is checked, so dependencies that depend on it again do not loop
        consistent.add(resource);
        long count = queue.getCount();
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            Candidate candidate = state.current(requirement, permutation);
            if (candidate == null || candidate.getProvider() == resource) {
                continue;
            }
            Fault fault = check(candidate.getProvider());
            if (fault != null) {
                // nothing was found to change below, so backtrack the choice of the dependency
                if (count == queue.getCount()) {
                    queue.add(PermutationType.IMPORT, state.permutate(requirement, permutation, fault));
                }
                return fault;
            }
        }
        return null;
    }

    /**
     * The same package imported from different providers: both imports are permutated.
     */
    private Fault permutateImports(ResolverResource resource, UsesViolation violation, Fault fault) {
        ResolverRequirement first = null;
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            Candidate candidate = state.current(requirement, permutation);
            if (candidate == null || !violation.getConflicting().contains(candidate.getCapability())) {
                continue;
            }
            if (first == null) {
                first = requirement;
                queue.add(PermutationType.IMPORT, state.permutate(requirement, permutation, fault));
            } else if (state.current(first, permutation).getCapability() != candidate.getCapability()) {
                queue.add(PermutationType.IMPORT, state.permutate(requirement, permutation, fault));
                break;
            }
        }
        return fault;
    }

    /**
     * Permutates for a package whose view of the resource conflicts with what the providers it sees imply: the
     * requirements on the chains that lead to the conflicting views are changed, once from the provider towards the
     * resource and once the other way round, and the imports of the resource itself are backtracked.
     *
     * @return false if there is a conflict to resolve
     */
    private boolean permutateUses(ResolverResource resource, UsesViolation violation, Fault fault) {
        boolean exported = false;
        for (ResolverCapability seen : violation.getSeen()) {
            exported |= seen.getOwner() == resource;
        }
        Set<ResolverRequirement> mutated = Collections
                .newSetFromMap(new IdentityHashMap<ResolverRequirement, Boolean>());
        Permutation[] permutations = new Permutation[2];
        // a violation that does not tell why is a conflict as well
        boolean conflict = violation.getChains().isEmpty();
        for (UsesChain chain : violation.getChains()) {
            for (List<ResolverRequirement> requirements : blames(chain, violation)) {
                if (takesAny(requirements.get(0), rootOf(chain), fault)) {
                    continue;
                }
                conflict = true;
                if (permutations[0] == null) {
                    permutations[0] = permutation.copy();
                    permutations[1] = permutation.copy();
                }
                for (int i = requirements.size() - 1; i >= 0; i--) {
                    if (permutate(requirements.get(i), mutated, permutations[0], fault)) {
                        break;
                    }
                }
                for (int i = 0; i < requirements.size(); i++) {
                    if (permutate(requirements.get(i), mutated, permutations[1], fault)) {
                        break;
                    }
                }
            }
        }
        if (conflict) {
            if (!mutated.isEmpty()) {
                queue.add(PermutationType.USES, permutations[0]);
                queue.add(PermutationType.USES, permutations[1]);
            }
            if (!exported) {
                for (ResolverRequirement own : ownRequirements(resource, violation)) {
                    if (!mutated.contains(own)) {
                        queue.permutateIfNeeded(PermutationType.IMPORT, own, permutation, fault);
                    }
                }
            }
        }
        return !conflict;
    }

    /**
     * The requirements that lead from the resource to what the capability at the end of the chain expects: the ones
     * of the chain, and the one of the owner of that capability that is wired to an implied capability of the package.
     */
    private List<List<ResolverRequirement>> blames(UsesChain chain, UsesViolation violation) {
        List<ResolverRequirement> requirements = new ArrayList<>();
        for (Optional<UsesChain> link = Optional.of(chain); link.isPresent(); link = link.get().getParent()) {
            link.get().getRequirement().ifPresent(requirements::add);
        }
        Collections.reverse(requirements);
        List<List<ResolverRequirement>> blames = new ArrayList<>();
        ResolverResource owner = chain.getCapability().getOwner();
        for (ResolverCapability implied : violation.getImplied()) {
            ResolverRequirement supplier = supplierOf(owner, implied);
            if (supplier != null && implied.getOwner() != owner) {
                List<ResolverRequirement> blame = new ArrayList<>(requirements);
                blame.add(supplier);
                blames.add(blame);
            }
        }
        if (blames.isEmpty() && !requirements.isEmpty()) {
            blames.add(requirements);
        }
        return blames;
    }

    /**
     * The requirement of the resource that is wired to the capability, an import or the required bundle.
     */
    private ResolverRequirement supplierOf(ResolverResource resource, ResolverCapability capability) {
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            Candidate candidate = state.current(requirement, permutation);
            if (candidate == null) {
                continue;
            }
            if (BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())
                    ? candidate.getProvider() == capability.getOwner() : candidate.getCapability() == capability) {
                return requirement;
            }
        }
        return null;
    }

    /**
     * The capability the resource itself sees at the start of the chain.
     */
    private static ResolverCapability rootOf(UsesChain chain) {
        UsesChain root = chain;
        while (root.getParent().isPresent()) {
            root = root.getParent().get();
        }
        return root.getCapability();
    }

    /**
     * The requirements of the resource that are wired to what it sees of the violated package.
     */
    private List<ResolverRequirement> ownRequirements(ResolverResource resource, UsesViolation violation) {
        List<ResolverRequirement> own = new ArrayList<>();
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            Candidate candidate = state.current(requirement, permutation);
            if (candidate == null) {
                continue;
            }
            boolean bundle = BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace());
            for (ResolverCapability seen : violation.getSeen()) {
                if (bundle ? seen.getOwner() == candidate.getProvider() : seen == candidate.getCapability()) {
                    own.add(requirement);
                    break;
                }
            }
        }
        return own;
    }

    /**
     * One requirement of a chain is permutated for a blame: not again if it already was, not if it takes any.
     */
    private boolean permutate(ResolverRequirement requirement, Set<ResolverRequirement> mutated, Permutation target,
            Fault fault) {
        if (requirement.isMultiple()) {
            return false;
        }
        if (mutated.contains(requirement)) {
            return true;
        }
        if (state.canRemove(requirement, target)) {
            target.remove(state.current(requirement, target), fault);
            mutated.add(requirement);
            return true;
        }
        return false;
    }

    /**
     * A requirement that takes any provider does not have to give up all of them: the ones that cause the blame are
     * left out as long as one remains.
     *
     * @return true if the blame is dismissed by that
     */
    private boolean takesAny(ResolverRequirement requirement, ResolverCapability root, Fault fault) {
        if (!requirement.isMultiple()) {
            return false;
        }
        Set<Candidate> removed = multiple.get(requirement);
        if (removed == null) {
            removed = new LinkedHashSet<>();
            multiple.put(requirement, removed);
        }
        boolean bundle = BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace());
        int remaining = 0;
        for (Candidate candidate : state.usable(requirement)) {
            if (candidate.getCapability() == root || bundle && candidate.getProvider() == root.getOwner()) {
                removed.add(candidate);
            }
            if (!removed.contains(candidate)) {
                remaining++;
            }
        }
        return remaining > 0;
    }
}
