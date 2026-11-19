/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The static part of the uses constraints, built once per resolve from
 * everything a resolver can ever choose from. It only holds facts that do not
 * change with a choice, so a question about one package or one resource is
 * answered by a lookup instead of a scan over the resources, and it is shared
 * by all permutations or rounds of a resolver. <ul> <li>the package imports of
 * a resource by package name and the importers of a provider,</li> <li>the
 * package names that are <em>contested</em>: more than one capability exists
 * that a resource can see for it, so a uses constraint on the package can be
 * violated at all,</li> <li>the package names whose class space <em>reaches a
 * contested package</em> through the uses of everything that can provide them:
 * a uses chain over names that do not is consistent no matter what is
 * chosen.</li> </ul> Only depends on the standard resource API, resources,
 * requirements and capabilities are keyed by identity because the value based
 * {@code equals} of the model implementations is deep and expensive.
 */
public final class UsesIndex {


    private final Map<Resource, Map<String, List<Requirement>>> importsByName = new IdentityHashMap<>();
    private final Map<Resource, Set<Resource>> importersOf = new IdentityHashMap<>();
    private final Map<Resource, List<Requirement>> imports = new IdentityHashMap<>();
    private final List<Resource> indexed = new ArrayList<>();
    private final Map<Resource, List<Requirement>> bundles = new IdentityHashMap<>();
    private final Map<Resource, List<Requirement>> generics = new IdentityHashMap<>();
    private final Map<String, Set<Capability>> providers = new HashMap<>();
    private final Map<String, Set<String>> usedBy = new HashMap<>();
    private final Set<String> reaching = new HashSet<>();

    /**
     * Indexes the resources reachable from the roots, without fragments.
     *
     * @param roots the resources to start from, everything reachable through
     *     the candidates is indexed
     * @param candidates what a resolver may choose from for a requirement
     * @param expand tells if the requirements of a resource are followed, an
     *     already resolved resource only contributes its capabilities
     */
    public UsesIndex(Collection<? extends Resource> roots, CandidateSource candidates, Predicate<Resource> expand) {
        this(roots, candidates, expand, null);
    }

    /**
     * Indexes the resources reachable from the roots, including the fragments
     * that may be attached to them.
     *
     * @param roots the resources to start from, everything reachable through
     *     the candidates is indexed
     * @param candidates what a resolver may choose from for a requirement
     * @param expand tells if the requirements of a resource are followed, an
     *     already resolved resource only contributes its capabilities
     * @param fragments the fragments that may be attached to a host, their
     *     requirements are imports of the host as well (the fragment is indexed
     *     on its own too), {@code null} if there are none
     */
    public UsesIndex(Collection<? extends Resource> roots, CandidateSource candidates, Predicate<Resource> expand,
            Function<Resource, ? extends Collection<? extends Resource>> fragments) {
        Set<Resource> seen = Collections.newSetFromMap(new IdentityHashMap<Resource, Boolean>());
        Deque<Resource> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Resource resource = todo.pop();
            if (!seen.add(resource)) {
                continue;
            }
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                provide(capability);
            }
            if (expand.test(resource)) {
                index(resource, candidates, todo, fragments);
            }
        }
        computeReaching();
    }

    private void index(Resource resource, CandidateSource candidates, Deque<Resource> todo,
            Function<Resource, ? extends Collection<? extends Resource>> fragments) {
        Map<String, List<Requirement>> byName = new HashMap<>();
        List<Requirement> all = new ArrayList<>();
        List<Requirement> required = new ArrayList<>();
        List<Requirement> generic = new ArrayList<>();
        List<Requirement> requirements = new ArrayList<>(resource.getRequirements(null));
        Collection<? extends Resource> attached = fragments == null ? null : fragments.apply(resource);
        if (attached != null) {
            for (Resource fragment : attached) {
                for (Requirement requirement : fragment.getRequirements(null)) {
                    if (!HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                        requirements.add(requirement);
                    }
                }
                todo.push(fragment);
            }
        }
        for (Requirement requirement : requirements) {
            if (PackageNamespace.RESOLUTION_DYNAMIC.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE))) {
                continue;
            }
            List<? extends Capability> caps = candidates.getCandidates(requirement);
            if (caps == null) {
                continue;
            }
            for (Capability capability : caps) {
                Set<Resource> importers = importersOf.get(capability.getResource());
                if (importers == null) {
                    importers = Collections.newSetFromMap(new IdentityHashMap<Resource, Boolean>());
                    importersOf.put(capability.getResource(), importers);
                }
                importers.add(resource);
                todo.push(capability.getResource());
                if (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
                    provide(capability);
                }
            }
            if (BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())
                    && !Namespace.CARDINALITY_MULTIPLE.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE)) && !caps.isEmpty()) {
                required.add(requirement);
            }
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace()) && !BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())
                    && !HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace()) && !caps.isEmpty()) {
                generic.add(requirement);
            }
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace()) && !Namespace.CARDINALITY_MULTIPLE.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE))
                    && !caps.isEmpty()) {
                String name = packageName(caps.get(0));
                List<Requirement> list = byName.get(name);
                if (list == null) {
                    list = new ArrayList<>(1);
                    byName.put(name, list);
                }
                list.add(requirement);
                all.add(requirement);
            }
        }
        importsByName.put(resource, byName);
        imports.put(resource, all);
        indexed.add(resource);
        bundles.put(resource, required);
        generics.put(resource, generic);
    }

    private void provide(Capability capability) {
        String name = packageName(capability);
        Set<Capability> set = providers.get(name);
        if (set == null) {
            set = Collections.newSetFromMap(new IdentityHashMap<Capability, Boolean>());
            providers.put(name, set);
        }
        if (set.add(capability)) {
            if (set.size() == 2) {
                reaching.add(name);
            }
            for (String used : uses(capability)) {
                Set<String> users = usedBy.get(used);
                if (users == null) {
                    users = new HashSet<>();
                    usedBy.put(used, users);
                }
                users.add(name);
            }
        }
    }

    /**
     * A package name reaches a contested one if a provider of it uses a package
     * name that does.
     */
    private void computeReaching() {
        Deque<String> todo = new ArrayDeque<>(reaching);
        while (!todo.isEmpty()) {
            Set<String> users = usedBy.get(todo.pop());
            if (users != null) {
                for (String user : users) {
                    if (reaching.add(user)) {
                        todo.push(user);
                    }
                }
            }
        }
    }

    private static String packageName(Capability capability) {
        return String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
    }

    /**
     * Decodes the {@code uses} directive of a capability.
     *
     * @param capability the capability, usually of the package namespace
     * @return the package names of the uses directive of a capability, empty if
     * it has none
     */
    public static List<String> uses(Capability capability) {
        String directive = capability.getDirectives().get(PackageNamespace.CAPABILITY_USES_DIRECTIVE);
        if (directive == null || directive.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String name : directive.split(",")) {
            name = name.trim();
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * The resources whose requirements were indexed.
     * These are the resources the index was built for and every resource
     * reached from them for which the {@code expand} predicate answered
     * {@code true}.
     *
     * @return the indexed resources in the order they were found
     */
    public List<Resource> getIndexedResources() {
        return Collections.unmodifiableList(indexed);
    }

    /**
     * The package imports of a resource.
     *
     * @param resource the resource
     * @return the package imports of the resource that are not of cardinality
     * multiple, in declaration order
     */
    public List<Requirement> imports(Resource resource) {
        List<Requirement> list = imports.get(resource);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * The bundle requirements of a resource.
     *
     * @param resource the resource
     * @return the single cardinality bundle requirements (Require-Bundle) of
     * the resource that have a candidate
     */
    public List<Requirement> bundleRequirements(Resource resource) {
        List<Requirement> list = bundles.get(resource);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * The requirements of a resource in namespaces other than package, bundle
     * and host.
     *
     * @param resource the resource
     * @return the requirements of the resource in other namespaces that have a
     * candidate (osgi.implementation, osgi.extender, ...): what their
     * capabilities use is part of the class space of the resource as well
     */
    public List<Requirement> genericRequirements(Resource resource) {
        List<Requirement> list = generics.get(resource);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * The imports of a resource for one package.
     *
     * @param resource the resource
     * @param packageName the name of the package
     * @return the single cardinality package imports of the resource for the
     * package name
     */
    public List<Requirement> importsOf(Resource resource, String packageName) {
        Map<String, List<Requirement>> byName = importsByName.get(resource);
        List<Requirement> list = byName == null ? null : byName.get(packageName);
        return list == null ? Collections.<Requirement> emptyList() : list;
    }

    /**
     * The resources that depend on a provider.
     *
     * @param provider the resource that offers capabilities
     * @return the resources that have a requirement with a capability of the
     * provider as candidate
     */
    public Set<Resource> importers(Resource provider) {
        Set<Resource> set = importersOf.get(provider);
        return set == null ? Collections.<Resource> emptySet() : set;
    }

    /**
     * Tells if a package has more than one provider.
     *
     * @param packageName the name of the package
     * @return true if more than one capability exists for the package, only
     * then it can be inconsistent
     */
    public boolean isContested(String packageName) {
        Set<Capability> set = providers.get(packageName);
        return set != null && set.size() > 1;
    }

    /**
     * The package names whose class space reaches one of the given names
     * through the uses of what can provide them.
     *
     * @param names the names to start from
     * @return the names and every name that uses them, directly or not
     */
    public Set<String> reachingFrom(Collection<String> names) {
        Set<String> result = new HashSet<>(names);
        Deque<String> todo = new ArrayDeque<>(names);
        while (!todo.isEmpty()) {
            Set<String> users = usedBy.get(todo.pop());
            if (users != null) {
                for (String user : users) {
                    if (result.add(user)) {
                        todo.push(user);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Tells if a package can be part of an inconsistent class space.
     *
     * @param packageName the name of the package
     * @return true if the package is contested or a capability that provides it
     * uses a package that reaches a contested one, if not nothing a resolver
     * chooses can make the class space inconsistent through it
     */
    public boolean reachesContested(String packageName) {
        return reaching.contains(packageName);
    }

    /**
     * Tells if any of several packages can be part of an inconsistent class
     * space.
     *
     * @param packageNames the names of the packages
     * @return true if any of the package names reaches a contested one, see
     * {@link #reachesContested(String)}
     */
    public boolean reachesContested(Collection<String> packageNames) {
        for (String name : packageNames) {
            if (reaching.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
