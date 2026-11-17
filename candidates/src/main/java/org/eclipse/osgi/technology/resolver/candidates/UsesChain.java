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

import java.util.Optional;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * Why a resource sees a capability: a resource sees it, either because it
 * provides the capability itself or because a requirement of it is wired to the
 * capability, and the capability is the one of another chain that uses a
 * package. Following {@link #getParent()} leads from the capability back to what
 * the resource sees itself, every requirement on the way is a place where a
 * different provider changes the view.
 */
public final class UsesChain {

    private final ResolverResource viewer;
    private final ResolverCapability capability;
    private final UsesChain parent;
    private Optional<ResolverRequirement> requirement;

    /**
     * Creates a link of a chain.
     *
     * @param viewer the resource that sees the capability
     * @param capability the capability that is seen
     * @param parent the link of the capability that uses a package this
     *     capability provides, {@code null} if the viewer is the resource the
     *     chain is about
     */
    public UsesChain(ResolverResource viewer, ResolverCapability capability,
            UsesChain parent) {
        this.viewer = viewer;
        this.capability = capability;
        this.parent = parent;
    }

    /**
     * The resource that sees the capability: the resource the chain is about
     * for the first link, else the owner of the capability of the link before.
     *
     * @return the resource
     */
    public ResolverResource getViewer() {
        return viewer;
    }

    /**
     * The capability that is seen.
     *
     * @return the capability
     */
    public ResolverCapability getCapability() {
        return capability;
    }

    /**
     * The link of the capability that uses a package this capability provides.
     *
     * @return the link, empty for the capability the resource sees itself
     */
    public Optional<UsesChain> getParent() {
        return Optional.ofNullable(parent);
    }

    /**
     * The requirement of the viewer the capability is seen through: an import,
     * or the required bundle that provides it. The requirement is looked up
     * when asked, not while the chain is built.
     *
     * @return the requirement, empty if the viewer provides the capability itself
     */
    public Optional<ResolverRequirement> getRequirement() {
        if (requirement == null) {
            requirement = Optional.ofNullable(lookup());
        }
        return requirement;
    }

    private ResolverRequirement lookup() {
        Object name = capability.getAttributes()
                .get(PackageNamespace.PACKAGE_NAMESPACE);
        for (ResolverRequirement candidate : viewer
                .getClassSpaceRequirements()) {
            if (!candidate.isEffective()) {
                continue;
            }
            boolean bundle = BundleNamespace.BUNDLE_NAMESPACE
                    .equals(candidate.getNamespace());
            for (Candidate selected : candidate.getCandidates()) {
                if (selected.getState() != CandidateState.SELECTED) {
                    continue;
                }
                if (!bundle && selected.getCapability() == capability) {
                    return candidate;
                }
                if (bundle && name != null
                        && selected.getProvider().getPackageSources(
                                String.valueOf(name)).contains(capability)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
