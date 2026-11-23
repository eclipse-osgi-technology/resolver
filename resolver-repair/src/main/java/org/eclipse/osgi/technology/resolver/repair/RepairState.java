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
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;

/**
 * The state of one run of a {@link RepairSearch}: the session it works on and what the search remembers while it runs.
 * A run is not shared between threads.
 */
final class RepairState {

    /**
     * The session the search works on.
     */
    final UsesSession session;

    /**
     * The resources that had an inconsistent class space when the current round started and are not repaired yet.
     */
    final Set<ResolverResource> inconsistent = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The requirements a kept repair of this run changed, the set is never cleared.
     * They were changed for a reason and a later conflict does not change them again, so repairs are never undone and
     * redone in turn and the number of attempts stays proportional to the number of conflicts.
     * The set only restricts the requirements behind the imports of the conflicting resource, its own imports can
     * still be changed, and a resource that is given up as a sacrifice adds nothing to it.
     */
    final Set<ResolverRequirement> settled = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Set when the search had an optional import it could have left unwired but the depth of this pass did not allow it.
     */
    boolean cutOff;

    /**
     * What {@link Choices#sees} found, as it asks about the same requirement and package for every conflict of a run
     * and the candidates of a requirement are the same whatever is selected, only looked up, never iterated.
     */
    final Map<ResolverRequirement, Map<String, Boolean>> seen = new IdentityHashMap<>();

    /**
     * Collects the resources a repair gives up, only while looking for a sacrifice.
     */
    private List<ResolverResource> sacrifices;

    /**
     * The same resources for the lookup by identity, as {@code equals} of a resource can be slow, never iterated.
     */
    private Set<ResolverResource> sacrificed;

    /**
     * The resources that were asked for explicitly, only looked up, never iterated.
     */
    private Set<ResolverResource> mandatory;

    RepairState(UsesSession session) {
        this.session = session;
    }

    /**
     * Starts to collect the resources a repair gives up instead of vetoing the change.
     */
    void startSacrificing() {
        sacrifices = new ArrayList<>();
        sacrificed = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * Stops collecting.
     *
     * @return the resources that were given up
     */
    List<ResolverResource> stopSacrificing() {
        List<ResolverResource> given = sacrifices;
        sacrifices = null;
        sacrificed = null;
        return given;
    }

    boolean isSacrificing() {
        return sacrifices != null;
    }

    /**
     * Gives up a resource.
     *
     * @param resource a resource that is lost with the change that is tried
     */
    void sacrifice(ResolverResource resource) {
        sacrifices.add(resource);
        sacrificed.add(resource);
    }

    boolean isSacrificed(ResolverResource resource) {
        return sacrificed != null && sacrificed.contains(resource);
    }

    /**
     * @return a mark to {@link #releaseSacrifices} what was given up after it
     */
    int sacrificeMark() {
        return sacrifices == null ? 0 : sacrifices.size();
    }

    /**
     * Takes back what was given up since the mark.
     *
     * @param mark a value of {@link #sacrificeMark()}
     */
    void releaseSacrifices(int mark) {
        if (sacrifices != null) {
            List<ResolverResource> released = sacrifices.subList(mark, sacrifices.size());
            sacrificed.removeAll(released);
            released.clear();
        }
    }

    boolean isMandatory(ResolverResource resource) {
        if (mandatory == null) {
            mandatory = Collections.newSetFromMap(new IdentityHashMap<>());
            mandatory.addAll(session.getMandatory());
        }
        return mandatory.contains(resource);
    }

    /**
     * The resources and everything that is wired to them, directly or not, they fail together.
     */
    Set<ResolverResource> dependents(Collection<ResolverResource> roots) {
        Map<ResolverResource, List<ResolverResource>> consumers = new IdentityHashMap<>();
        for (ResolverResource consumer : session.getResources()) {
            if (consumer.getState() != ResolveState.RESOLVED) {
                continue;
            }
            for (ResolverRequirement requirement : consumer.getResolverRequirements()) {
                Candidate selected = Choices.selectedOf(requirement);
                if (selected != null && !requirement.isOptional()) {
                    consumers.computeIfAbsent(selected.getProvider(), k -> new ArrayList<>()).add(consumer);
                }
            }
        }
        Set<ResolverResource> result = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ResolverResource> queue = new ArrayList<>(roots);
        while (!queue.isEmpty()) {
            ResolverResource next = queue.remove(queue.size() - 1);
            if (result.add(next)) {
                queue.addAll(consumers.getOrDefault(next, Collections.emptyList()));
            }
        }
        return result;
    }
}
