/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.candidates;

/**
 * A link of the graph: the capability offered for a requirement. The state can
 * change during the resolution, for example if the capability turns out to be
 * unsuitable.
 */
public class Candidate {

    private final ResolverRequirement requirement;
    private final ResolverCapability capability;
    private CandidateState state = CandidateState.AVAILABLE;
    private String reason;

    /**
     * Creates a candidate in state {@link CandidateState#AVAILABLE}.
     *
     * @param requirement the requirement the capability is offered for
     * @param capability the capability that is offered
     * @param reason why the capability is offered
     */
    Candidate(ResolverRequirement requirement, ResolverCapability capability, String reason) {
        this.requirement = requirement;
        this.capability = capability;
        this.reason = reason;
    }

    /**
     * The resource that is responsible if the candidate can not be used because
     * it can not be resolved: the provider or, for a capability of a fragment,
     * the fragment itself.
     *
     * @return the failed resource or {@code null} if none failed
     */
    public ResolverResource getFailedResource() {
        if (getProvider().getState() == ResolveState.FAILED) {
            return getProvider();
        }
        ResolverResource declaring = capability.getDeclaringResource();
        if (declaring.getState() == ResolveState.FAILED || declaring.isDetachedFrom(getProvider())) {
            return declaring;
        }
        return null;
    }

    /**
     * The requirement the capability is offered for.
     *
     * @return the requirement
     */
    public ResolverRequirement getRequirement() {
        return requirement;
    }

    /**
     * The capability that is offered.
     *
     * @return the offered capability
     */
    public ResolverCapability getCapability() {
        return capability;
    }

    /**
     * The resource that would provide the capability.
     *
     * @return the provider of the capability
     */
    public ResolverResource getProvider() {
        return capability.getOwner();
    }

    /**
     * What became of the candidate.
     *
     * @return the current state
     */
    public CandidateState getState() {
        return state;
    }

    /**
     * Why the candidate is in its current state.
     *
     * @return the reason for the current state
     */
    public String getReason() {
        return reason;
    }

    /**
     * Marks the candidate as chosen to satisfy the requirement.
     *
     * @param reason why the candidate is chosen
     */
    public void select(String reason) {
        change(CandidateState.SELECTED, reason);
    }

    /**
     * Excludes the candidate for good.
     *
     * @param reason why the candidate is excluded
     */
    public void reject(String reason) {
        change(CandidateState.REJECTED, reason);
    }

    /**
     * Excludes the candidate only because another candidate is preferred, it
     * is still an alternative if that one causes a conflict.
     *
     * @param reason why another candidate is preferred
     */
    public void defer(String reason) {
        change(CandidateState.DEFERRED, reason);
    }

    /**
     * Tells if the candidate is not selected but could be.
     *
     * @return true if the candidate is not selected but could be, unless its
     * provider failed
     */
    public boolean isAlternative() {
        return state == CandidateState.DEFERRED && getFailedResource() == null;
    }

    /**
     * Tells if the candidate is still taken into account, that is neither
     * rejected for good nor deferred.
     *
     * @return true if the state is {@link CandidateState#AVAILABLE} or
     * {@link CandidateState#SELECTED}
     */
    public boolean isIncluded() {
        return state == CandidateState.AVAILABLE || state == CandidateState.SELECTED;
    }

    /**
     * Brings the candidate back to a state it had before.
     *
     * @param state the state to restore
     * @param reason the reason to restore
     */
    public void restore(CandidateState state, String reason) {
        change(state, reason);
    }

    private void change(CandidateState newState, String newReason) {
        PackageViewCache cache = requirement.getOwner().getPackageViewCache();
        if (cache.isRecording()) {
            CandidateState oldState = this.state;
            String oldReason = this.reason;
            cache.record(() -> {
                this.state = oldState;
                this.reason = oldReason;
            });
        }
        this.state = newState;
        this.reason = newReason;
        cache.invalidate();
    }

    @Override
    public String toString() {
        return state + " " + capability + " of " + getProvider() + ": " + reason;
    }
}
