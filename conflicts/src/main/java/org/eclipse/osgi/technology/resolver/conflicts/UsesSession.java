/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.conflicts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;

/**
 * What a {@link UsesConflictStrategy} sees of a resolve operation after the
 * selection: the graph, the indices built from it and the rule it has to apply.
 * The graph is changed only through the candidate graph (the states and reasons
 * of the candidates, the attached fragments of a host) and the methods below.
 */
public interface UsesSession {

    /**
     * All resources of the graph.
     *
     * @return the resources in the order they were discovered
     */
    Collection<ResolverResource> getResources();

    /**
     * The resources the resolve context asked for explicitly.
     *
     * @return the resources that have to resolve
     */
    List<ResolverResource> getMandatory();

    /**
     * The resources the resolve context asked for optionally.
     *
     * @return the resources that resolve if they can
     */
    List<ResolverResource> getOptional();

    /**
     * The static facts about the uses directives.
     *
     * @return the index, for example to tell which packages are contested
     */
    UsesIndex getUsesIndex();

    /**
     * The choices that depend on each other through the uses directives.
     *
     * @return the graph, to tell which choices can matter for a conflict and
     *     what a change can affect
     */
    UsesGraph getUsesGraph();

    /**
     * The exports that can be discarded because their bundle imports the same
     * package.
     *
     * @return the index of the substitutions
     */
    SubstitutionIndex getSubstitutionIndex();

    /**
     * The class space rule: the first reason the class space of the resource is
     * not consistent, a discarded export it is wired to or a package it would
     * see from different providers. Reads only the state of the selection, so
     * it may be called for several resources in parallel.
     *
     * @param resource the resource
     * @return the violation, empty if the class space is consistent
     */
    Optional<UsesViolation> violationOf(ResolverResource resource);

    /**
     * Fails the resource with the violation and records it at the candidates
     * involved.
     *
     * @param resource the resource that can not be resolved
     * @param violation the reason
     */
    void fail(ResolverResource resource, UsesViolation violation);

    /**
     * A selected provider that failed is no longer wired, a mandatory
     * requirement without one fails its resource, until nothing changes.
     *
     * @return the requirements that lost a selected provider
     */
    Collection<ResolverRequirement> propagateFailures();

    /**
     * What has to be checked again after some choices changed.
     *
     * @param changed the requirements whose selected candidate changed
     * @return the owners of the changed requirements and of everything that can
     *     see them through the uses graph or a substitution, a fragment
     *     counting for its hosts, in a stable order
     */
    Collection<ResolverResource> affected(Collection<ResolverRequirement> changed);
}
