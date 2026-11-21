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

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * A change of the selected candidate of a requirement made to avoid a uses conflict, taken back with the
 * {@link org.eclipse.osgi.technology.resolver.candidates.Checkpoint} of the attempt.
 * The reasons recorded in the two candidates are the explanation of the change, they name the resource whose
 * conflict caused it, even if that is not the owner of the requirement.
 */
final class CandidateSwap {

    private final ResolverRequirement requirement;
    private final Candidate selected;
    private final Candidate alternative;

    private CandidateSwap(ResolverRequirement requirement, Candidate selected, Candidate alternative, String selectedMessage,
            String alternativeMessage) {
        this.requirement = requirement;
        this.selected = selected;
        this.alternative = alternative;
        selected.defer(selectedMessage);
        if (alternative != null) {
            alternative.select(alternativeMessage);
        }
    }

    /**
     * Moves the owner of the requirement from the provider of the selected candidate to the provider of the
     * alternative for all its imports that both offer: packages of a provider belong together (they use each other),
     * so moving only one of them would leave the owner with a mix that conflicts again.
     */
    static void move(RepairState state, SwapPath path, ResolverRequirement requirement, Candidate selected,
            Candidate alternative, ResolverResource conflicting, UsesViolation violation) {
        path.add(replace(state, requirement, selected, alternative, conflicting, violation));
        String cause = describe(alternative);
        for (ResolverRequirement other : requirement.getOwner().getClassSpaceRequirements()) {
            if (other == requirement || !Choices.isChoice(other) || path.contains(other)) {
                continue;
            }
            Candidate otherSelected = Choices.selectedOf(other);
            if (otherSelected == null || otherSelected.getProvider() != selected.getProvider()) {
                continue;
            }
            for (Candidate otherAlternative : other.getCandidates()) {
                if (otherAlternative.isAlternative() && otherAlternative.getProvider() == alternative.getProvider()) {
                    path.add(together(other, otherSelected, otherAlternative, cause));
                    break;
                }
            }
        }
    }

    /**
     * A change that follows another one: the owner takes all imports that the provider offers from the same
     * provider, and the reason names the import that made the move necessary.
     */
    private static CandidateSwap together(ResolverRequirement requirement, Candidate selected, Candidate alternative,
            String cause) {
        String owner = requirement.getOwner().getName();
        String provider = alternative.getProvider().getName();
        String reason = owner + " has to take " + cause + " from " + provider + " to avoid an inconsistent class space, "
                + "so it takes the imports that " + provider + " offers as well and does not mix the providers";
        return new CandidateSwap(requirement, selected, alternative,
                "Replaced by " + provider + " together with the import of " + cause + ": " + reason,
                "Selected instead of " + selected.getProvider().getName() + " together with the import of " + cause + ": "
                        + reason);
    }

    private static String describe(Candidate candidate) {
        Object name = candidate.getCapability().getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
        return name != null ? name.toString() : candidate.getCapability().getNamespace();
    }

    /**
     * Leaves an optional requirement unwired, as no candidate can be used without an inconsistent class space.
     * The requirement is treated like a mandatory one until then, an optional import is only given up as a last choice.
     */
    static CandidateSwap drop(ResolverRequirement requirement, Candidate selected, ResolverResource conflicting) {
        return new CandidateSwap(requirement, selected, null, "Left unwired to avoid an inconsistent "
                + cause(requirement, conflicting) + ", the import is optional", null);
    }

    /**
     * A swap that tells in its journal entries what caused it: a discarded export or an inconsistent class space.
     */
    private static CandidateSwap replace(RepairState state, ResolverRequirement requirement, Candidate selected,
            Candidate alternative, ResolverResource conflicting, UsesViolation violation) {
        Substitution substitution = Choices.substitutionOf(state, violation);
        if (substitution == null) {
            return new CandidateSwap(requirement, selected, alternative, "Replaced by " + alternative.getProvider().getName()
                    + " to avoid an inconsistent " + cause(requirement, conflicting), "Selected instead of "
                    + selected.getProvider().getName() + ", that one would make the " + cause(requirement, conflicting)
                    + " inconsistent");
        }
        String exporter = substitution.getExport().getResource() instanceof ResolverResource
                ? ((ResolverResource) substitution.getExport().getResource()).getName() : "the exporter";
        String consumer = violation.getHiddenImport().get().getOwner().getName();
        if (requirement == substitution.getOverlappingImport()) {
            return new CandidateSwap(requirement, selected, alternative,
                    "Not used so that " + exporter + " keeps its export that " + consumer + " needs",
                    "Selected instead of " + selected.getProvider().getName() + " so that " + exporter
                            + " keeps its export that " + consumer + " needs");
        }
        return new CandidateSwap(requirement, selected, alternative,
                "Replaced by " + alternative.getProvider().getName() + " as " + exporter + " discards its export, "
                        + "it imports the package from another provider",
                "Selected instead of " + selected.getProvider().getName() + ", that one discards its export");
    }

    private static String cause(ResolverRequirement requirement, ResolverResource conflicting) {
        return conflicting == requirement.getOwner() ? "class space of " + conflicting.getName()
                : "class space of " + conflicting.getName() + " that sees the choice of " + requirement.getOwner().getName();
    }

    /**
     * @return the requirement whose selected candidate was changed
     */
    ResolverRequirement getRequirement() {
        return requirement;
    }

    /**
     * @return the candidate that was selected before the change
     */
    Candidate getDropped() {
        return selected;
    }

    /**
     * @return the candidate selected instead, {@code null} if the requirement is left unwired
     */
    Candidate getAlternative() {
        return alternative;
    }
}
