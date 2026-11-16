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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.index.CandidateSource;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The uses constraints seen from the choices: a node is a package import or a
 * bundle requirement (a choice point of a resolver), an edge {@code r -> r'}
 * says that a candidate of {@code r} is a capability of a resource {@code Q}
 * that uses a package {@code p}, and {@code r'} is the import of {@code Q} for
 * {@code p}. So whatever is chosen for {@code r'} can decide whether the choice
 * for {@code r} is consistent.
 * <p>
 * The edges exist for every candidate, the graph does not depend on a selection
 * and is built once like the {@link UsesIndex}. Only edges over package names
 * that reach a contested package are kept, all other chains can not become
 * inconsistent. A requirement of another namespace (for example
 * {@code osgi.implementation}) is a node as well, as what its capability uses
 * is part of the class space, but it has no choice of its own. A bundle
 * requirement decides what its owner sees of the exports of the required bundle
 * and of what that reexports, so it is linked to the imports behind these
 * exports. Uses of fragments are not covered, a user of this graph has to treat
 * these separately.
 * <ul>
 * <li>{@link #affected(Iterable)} is the set of imports whose consistency can
 * change if the given imports change, so the dirty set of an incremental
 * check,</li>
 * <li>{@link #dependencies(Requirement)} are the imports that can matter for an
 * import, the choice points of a conflict path.</li>
 * </ul>
 * Keyed by identity like the index.
 */
public final class UsesGraph {

    private final Map<Requirement, List<Requirement>> dependencies = new IdentityHashMap<>();
    private final Map<Requirement, List<Requirement>> dependents = new IdentityHashMap<>();

    /**
     * Links the choices of the resources of the index.
     *
     * @param index the static facts about the uses directives, tells which
     *     package names can matter
     * @param candidates what a resolver may choose from for a requirement
     */
    public UsesGraph(UsesIndex index, CandidateSource candidates) {
        for (Resource resource : index.getIndexedResources()) {
            List<Requirement> sources = new ArrayList<>(index.imports(resource));
            sources.addAll(index.genericRequirements(resource));
            for (Requirement requirement : sources) {
                List<? extends Capability> caps = candidates.getCandidates(requirement);
                if (caps == null) {
                    continue;
                }
                for (Capability capability : caps) {
                    for (String used : UsesIndex.uses(capability)) {
                        if (!index.reachesContested(used)) {
                            continue;
                        }
                        List<Requirement> targets = index.importsOf(capability.getResource(), used);
                        for (Requirement target : targets) {
                            link(requirement, target);
                        }
                        // without an import the exporter may get the package through a required bundle
                        if (targets.isEmpty()) {
                            for (Requirement target : index.bundleRequirements(capability.getResource())) {
                                link(requirement, target);
                            }
                        }
                    }
                }
            }
            for (Requirement requirement : index.bundleRequirements(resource)) {
                for (Capability capability : candidates.getCandidates(requirement)) {
                    addBundleEdges(index, requirement, capability.getResource());
                }
            }
        }
    }

    /**
     * What a resource sees through a required bundle is decided by where that
     * bundle gets its exported packages (an export it imports as well is
     * substituted), by what its exports use, and by the bundles it reexports.
     *
     * @param index the static facts about the uses directives
     * @param requirement the bundle requirement
     * @param provider the bundle that is a candidate for the requirement
     */
    private void addBundleEdges(UsesIndex index, Requirement requirement, Resource provider) {
        boolean relevant = false;
        for (Capability export : provider.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
            String name = String.valueOf(export.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
            boolean reaches = index.reachesContested(name);
            relevant |= reaches;
            if (reaches) {
                for (Requirement target : index.importsOf(provider, name)) {
                    link(requirement, target);
                }
            }
            for (String used : UsesIndex.uses(export)) {
                if (index.reachesContested(used)) {
                    relevant = true;
                    for (Requirement target : index.importsOf(provider, used)) {
                        link(requirement, target);
                    }
                }
            }
        }
        for (Requirement next : index.bundleRequirements(provider)) {
            // the provider's own requirements decide what it exports through
            // split packages and what it reexports
            if (relevant || isReexport(next)) {
                link(requirement, next);
            }
        }
    }

    /**
     * Records that the choice for a requirement depends on another one.
     *
     * @param requirement the requirement whose candidate uses a package
     * @param target the requirement that decides where the package comes from
     */
    private void link(Requirement requirement, Requirement target) {
        if (target != requirement) {
            add(dependencies, requirement, target);
            add(dependents, target, requirement);
        }
    }

    /**
     * Adds a value to the list of a key, once.
     *
     * @param map the edges
     * @param key the requirement the list belongs to
     * @param value the requirement to add
     */
    private void add(Map<Requirement, List<Requirement>> map, Requirement key, Requirement value) {
        List<Requirement> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>(2);
            map.put(key, list);
        }
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    /**
     * The choices a requirement depends on directly.
     *
     * @param requirement the requirement
     * @return the imports that a candidate of the import uses a package of, the
     *     direct choice points behind it
     */
    public List<Requirement> dependencies(Requirement requirement) {
        List<Requirement> list = dependencies.get(requirement);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * The choices that depend directly on a requirement.
     *
     * @param requirement the requirement
     * @return the imports that have a candidate that uses a package this import
     *     decides about
     */
    public List<Requirement> dependents(Requirement requirement) {
        List<Requirement> list = dependents.get(requirement);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * What has to be looked at again if some choices change.
     *
     * @param changed the requirements whose selected candidate changed
     * @return the changed imports and all imports that transitively depend on
     *     them, each once, in the order they are found: the order a resolver
     *     visits them in must not depend on identity hash codes, or its results
     *     would differ from run to run
     */
    public List<Requirement> affected(Iterable<? extends Requirement> changed) {
        Deque<Requirement> todo = new ArrayDeque<>();
        for (Requirement requirement : changed) {
            todo.addLast(requirement);
        }
        return walk(todo, dependents);
    }

    /**
     * Everything a choice depends on.
     *
     * @param start the requirement
     * @return the import and the imports it transitively depends on, each once,
     *     in the order they are found
     */
    public List<Requirement> reachable(Requirement start) {
        Deque<Requirement> todo = new ArrayDeque<>();
        todo.addLast(start);
        return walk(todo, dependencies);
    }

    /**
     * Collects what is reachable over the edges in breadth first order.
     *
     * @param todo the requirements to start from
     * @param edges the edges to follow
     * @return the requirements found, each once, in the order they are found
     */
    private static List<Requirement> walk(Deque<Requirement> todo, Map<Requirement, List<Requirement>> edges) {
        Set<Requirement> seen = Collections.newSetFromMap(new IdentityHashMap<Requirement, Boolean>());
        List<Requirement> result = new ArrayList<>();
        while (!todo.isEmpty()) {
            Requirement requirement = todo.pollFirst();
            if (seen.add(requirement)) {
                result.add(requirement);
                List<Requirement> next = edges.get(requirement);
                if (next != null) {
                    todo.addAll(next);
                }
            }
        }
        return result;
    }

    private static boolean isReexport(Requirement requirement) {
        return BundleNamespace.VISIBILITY_REEXPORT
                .equals(requirement.getDirectives().get(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE));
    }
}
