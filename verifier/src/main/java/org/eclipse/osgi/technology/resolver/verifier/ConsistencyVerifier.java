/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.verifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

/**
 * Independent check that a resolve result has consistent class spaces
 * (OSGi Core 3.7.5 "Package Constraints" and the "uses" directive of
 * 3.6.5). It works on the plain wires of a result, knows nothing about
 * candidates or permutations and never searches for a solution. It only
 * reports inconsistencies.
 * <p>
 * A resource sees a package as described by {@link ClassSpace}. A capability
 * that "uses" another package implies that every resource consuming the
 * capability sees that package from the same source as the provider of the
 * capability does, the implication is transitive.
 */
public final class ConsistencyVerifier {

    private final Map<Resource, List<Wire>> result;
    private final ClassSpace space;

    /**
     * Creates an verifier for a result.
     *
     * @param result the wires of each resolved resource
     */
    public ConsistencyVerifier(Map<Resource, List<Wire>> result) {
        this.result = result;
        this.space = new ClassSpace(result);
    }

    /**
     * Checks the class spaces of the result.
     *
     * @return a description of each inconsistency, empty if the result is consistent
     */
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        for (Resource resource : result.keySet()) {
            checkImportsAreUnique(resource, violations);
            for (String packageName : space.packages(resource)) {
                for (Capability capability : space.providers(resource, packageName)) {
                    checkUses(resource, packageName, capability, violations);
                }
            }
            for (Wire wire : space.wires(resource)) {
                String namespace = wire.getCapability().getNamespace();
                if (!PackageNamespace.PACKAGE_NAMESPACE.equals(namespace)
                        && !BundleNamespace.BUNDLE_NAMESPACE.equals(namespace)) {
                    checkUses(resource, namespace, wire.getCapability(), violations);
                }
            }
        }
        return violations;
    }

    private void checkImportsAreUnique(Resource resource, List<String> violations) {
        Map<String, Set<Capability>> imported = new HashMap<>();
        for (Wire wire : space.wires(resource)) {
            if (ClassSpace.isImport(resource, wire)) {
                imported.computeIfAbsent(ClassSpace.packageName(wire.getCapability()), k -> new LinkedHashSet<>())
                        .add(wire.getCapability());
            }
        }
        imported.forEach((name, caps) -> {
            if (caps.size() > 1 && !allCompatible(caps)) {
                violations.add(describe(resource) + " imports package " + name + " from different sources");
            }
        });
    }

    /**
     * Checks the "uses" implications of a capability (and of everything it
     * transitively implies) against the class space of the consumer.
     */
    private void checkUses(Resource consumer, String reason, Capability root, List<String> violations) {
        Set<Capability> visited = new HashSet<>();
        Set<Capability> pending = new LinkedHashSet<>(space.sourcesOf(root, new HashSet<>()));
        while (!pending.isEmpty()) {
            Capability current = pending.iterator().next();
            pending.remove(current);
            if (!visited.add(current) || space.owner(current).equals(consumer)) {
                continue;
            }
            for (String used : uses(current)) {
                Set<Capability> implied = impliedSources(space.owner(current), used);
                for (Set<Capability> actual : actualSources(consumer, used)) {
                    if (!implied.isEmpty() && !actual.isEmpty() && !compatible(implied, actual)) {
                        violations.add(describe(consumer) + " sees package " + used + " from " + describe(actual)
                                + " but " + reason + " from " + describe(space.owner(root)) + " implies "
                                + describe(implied));
                    }
                }
                pending.addAll(implied);
            }
        }
    }

    /** Two views of a package agree if one source set contains the other (split packages are merged). */
    private boolean compatible(Set<Capability> a, Set<Capability> b) {
        Set<Object> keysA = classSources(a);
        Set<Object> keysB = classSources(b);
        return keysA.containsAll(keysB) || keysB.containsAll(keysA);
    }

    /**
     * A bundle that exports a package several times (for example in the versions 2.6, 3.0 and 3.1) has one class loader
     * for it, so all of these capabilities are the same source of classes.
     */
    private Set<Object> classSources(Set<Capability> capabilities) {
        Set<Object> keys = new HashSet<>();
        for (Capability capability : capabilities) {
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
                keys.add(Arrays.asList(space.owner(capability), ClassSpace.packageName(capability)));
            } else {
                keys.add(capability);
            }
        }
        return keys;
    }

    private boolean allCompatible(Set<Capability> caps) {
        Set<Capability> first = space.sourcesOf(caps.iterator().next(), new HashSet<>());
        for (Capability cap : caps) {
            if (!compatible(first, space.sourcesOf(cap, new HashSet<>()))) {
                return false;
            }
        }
        return true;
    }

    private Set<Capability> union(Set<Capability> capabilities) {
        Set<Capability> sources = new LinkedHashSet<>();
        capabilities.forEach(c -> sources.addAll(space.sourcesOf(c, new HashSet<>())));
        return sources;
    }

    /** What a resource provides for a package to whoever uses it: its export, else required, else imported. */
    private Set<Capability> impliedSources(Resource resource, String packageName) {
        Set<Capability> capabilities = space.exported(resource, packageName);
        if (capabilities.isEmpty()) {
            capabilities = space.required(resource, packageName);
        }
        if (capabilities.isEmpty()) {
            capabilities = space.imported(resource, packageName);
        }
        return union(capabilities);
    }

    /** The separately checked views of a package a resource has: through its export and through imports or required bundles. */
    private List<Set<Capability>> actualSources(Resource resource, String packageName) {
        List<Set<Capability>> views = new ArrayList<>();
        Set<Capability> own = space.exported(resource, packageName);
        if (!own.isEmpty()) {
            views.add(union(own));
        }
        Set<Capability> other = space.imported(resource, packageName);
        if (other.isEmpty()) {
            other = space.required(resource, packageName);
        }
        if (!other.isEmpty()) {
            views.add(union(other));
        }
        return views;
    }

    private List<String> uses(Capability capability) {
        String uses = capability.getDirectives().get(PackageNamespace.CAPABILITY_USES_DIRECTIVE);
        List<String> packages = new ArrayList<>();
        if (uses != null) {
            for (String name : uses.split(",")) {
                if (!name.trim().isEmpty()) {
                    packages.add(name.trim());
                }
            }
        }
        return packages;
    }

    private String describe(Resource resource) {
        return resource.getCapabilities("osgi.identity").isEmpty() ? resource.toString()
                : String.valueOf(resource.getCapabilities("osgi.identity").get(0).getAttributes().get("osgi.identity"));
    }

    private String describe(Set<Capability> capabilities) {
        List<String> names = new ArrayList<>();
        capabilities.forEach(c -> names.add(describe(space.owner(c))));
        return names.toString();
    }
}
