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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A {@link Requirement} of a {@link ResolverResource}, it knows the
 * {@link Candidate}s offered for it. Candidates are added while the graph is
 * built and stay in the order of preference.
 */
public class ResolverRequirement implements Requirement {

    private final ResolverResource owner;
    private final Requirement wrapped;
    private final boolean optional;
    private final boolean multiple;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Candidate> view = Collections.unmodifiableList(candidates);
    private final boolean dynamic;
    private boolean effective;

    /**
     * Wraps a requirement of a resource, no candidates are offered yet.
     *
     * @param owner the resource that has the requirement
     * @param wrapped the requirement to wrap
     */
    ResolverRequirement(ResolverResource owner, Requirement wrapped) {
        this.owner = owner;
        this.wrapped = wrapped;
        Map<String, String> directives = wrapped.getDirectives();
        this.dynamic = PackageNamespace.RESOLUTION_DYNAMIC.equals(directives.get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE));
        this.effective = !dynamic;
        this.optional = Namespace.RESOLUTION_OPTIONAL.equals(directives.get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE));
        this.multiple = Namespace.CARDINALITY_MULTIPLE.equals(directives.get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE))
                || HostNamespace.HOST_NAMESPACE.equals(wrapped.getNamespace());
    }

    /**
     * The wrapped requirement.
     *
     * @return the wrapped requirement
     */
    public Requirement unwrap() {
        return wrapped;
    }

    /**
     * The resource that has the requirement.
     *
     * @return the owning resource
     */
    public ResolverResource getOwner() {
        return owner;
    }

    /**
     * True if the requirement has {@code resolution:=optional}.
     *
     * @return true if the requirement is optional
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Cardinality multiple, all suitable candidates are wired. A fragment is
     * attached to all its hosts.
     *
     * @return true if all suitable candidates are wired
     */
    public boolean isMultiple() {
        return multiple;
    }

    /**
     * Not effective requirements are ignored by the resolver, this is decided
     * by the resolve context.
     *
     * @return true if the requirement has to be considered
     */
    public boolean isEffective() {
        return effective;
    }

    /**
     * Sets whether the requirement is effective, see {@link #isEffective()}.
     *
     * @param effective true if the requirement has to be considered
     */
    public void setEffective(boolean effective) {
        this.effective = effective && !dynamic;
    }

    /**
     * Offers a capability for this requirement, only to be used while the graph
     * is built.
     *
     * @param capability the capability to offer
     * @return the new candidate
     */
    public Candidate addCandidate(ResolverCapability capability) {
        Candidate candidate = new Candidate(this, capability, "Offered by the resolve context");
        candidates.add(candidate);
        return candidate;
    }

    /**
     * Offers a capability at a position, only to be used while the graph is
     * built.
     *
     * @param index the position in the list of candidates
     * @param capability the capability to offer
     * @param reason why the capability is offered
     * @return the new candidate
     */
    public Candidate insertCandidate(int index, ResolverCapability capability, String reason) {
        Candidate candidate = new Candidate(this, capability, reason);
        candidates.add(index, candidate);
        return candidate;
    }

    /**
     * Takes back an offer, only to be used while the graph is built.
     *
     * @param index the position of the candidate to remove
     */
    public void removeCandidate(int index) {
        candidates.remove(index);
    }

    /**
     * The offered capabilities in order of preference.
     *
     * @return an unmodifiable view of the candidates
     */
    public List<Candidate> getCandidates() {
        return view;
    }

    @Override
    public String getNamespace() {
        return wrapped.getNamespace();
    }

    @Override
    public Map<String, String> getDirectives() {
        return wrapped.getDirectives();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return wrapped.getAttributes();
    }

    @Override
    public Resource getResource() {
        return owner;
    }

    @Override
    public String toString() {
        String filter = wrapped.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        return wrapped.getNamespace() + (filter == null ? "" : ":" + filter);
    }
}
