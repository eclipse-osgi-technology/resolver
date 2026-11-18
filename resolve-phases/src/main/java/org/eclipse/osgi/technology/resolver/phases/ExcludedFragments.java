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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.osgi.technology.resolver.failures.FragmentConflictException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.ResolutionException;

/**
 * The fragments that are not attached to a host, with the reason. It outlives
 * the graph of one attempt of a resolve, so it refers to the resources the
 * graph wraps.
 */
final class ExcludedFragments implements FragmentExclusions {

    private final Map<Resource, FragmentConflictException> conflicts = new LinkedHashMap<>();
    private final Map<Resource, ResolutionException> causes = new LinkedHashMap<>();
    private final Map<Resource, Map<Resource, String>> reasons = new LinkedHashMap<>();

    /**
     * Excludes a fragment from a host.
     *
     * @param fragment the fragment
     * @param host the host it is not attached to
     * @param reason why
     * @return {@code true} if the fragment was attached to the host before
     */
    boolean add(Resource fragment, Resource host, String reason) {
        return reasons.computeIfAbsent(fragment, k -> new LinkedHashMap<>()).putIfAbsent(host, reason) == null;
    }

    /**
     * Tells why a fragment is not attached to a host.
     *
     * @param fragment the fragment
     * @param host the host
     * @return the reason, empty if the fragment may be attached
     */
    @Override
    public Optional<String> reasonFor(Resource fragment, Resource host) {
        return Optional.ofNullable(reasons.getOrDefault(fragment, Collections.emptyMap()).get(host));
    }

    /**
     * Records the failure of the host that made the fragment be excluded.
     *
     * @param fragment the fragment
     * @param cause the failure, the first one recorded is kept
     */
    void setCause(Resource fragment, ResolutionException cause) {
        causes.putIfAbsent(fragment, cause);
    }

    /**
     * The failure of the host that made the fragment be excluded.
     *
     * @param fragment the fragment
     * @return the failure, empty if the fragment was not excluded
     */
    Optional<ResolutionException> causeOf(Resource fragment) {
        return Optional.ofNullable(causes.get(fragment));
    }

    /**
     * Records that a fragment that can not be left out makes the host
     * inconsistent. Only a uses violation can be blamed on a fragment.
     *
     * @param fragment the fragment
     * @param host the host
     * @param cause the failure of the host
     */
    void setConflict(Resource fragment, Resource host, ResolutionException cause) {
        if (cause instanceof UsesConstraintViolationException) {
            FragmentConflictException conflict = new FragmentConflictException((UsesConstraintViolationException) cause,
                    fragment, host);
            conflicts.putIfAbsent(fragment, conflict);
            conflicts.putIfAbsent(host, conflict);
        }
    }

    /**
     * The conflict a fragment that can not be left out has with a host.
     *
     * @param resource the fragment or the host
     * @return the conflict, empty if no fragment is to blame for it
     */
    Optional<FragmentConflictException> conflictOf(Resource resource) {
        return Optional.ofNullable(conflicts.get(resource));
    }
}
