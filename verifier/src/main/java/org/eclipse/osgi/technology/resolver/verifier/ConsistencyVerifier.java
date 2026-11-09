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
import java.util.Collections;
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
 * A resource sees a package either from its imports, from required bundles
 * (including reexports) or from its own export, in that order. A capability
 * that "uses" another package implies that every resource consuming the
 * capability sees that package from the same source as the provider of the
 * capability does, the implication is transitive.
 */
public final class ConsistencyVerifier {

    private final Map<Resource, List<Wire>> result;

    /**
     * Creates an verifier for a result.
     *
     * @param result the wires of each resolved resource
     */
    public ConsistencyVerifier(Map<Resource, List<Wire>> result) {
        this.result = result;
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
            for (String packageName : visiblePackages(resource)) {
                for (Capability capability : providersSeenBy(resource, packageName)) {
                    checkUses(resource, packageName, capability, violations);
                }
            }
            for (Wire wire : wires(resource)) {
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
        for (Wire wire : wires(resource)) {
            if (isImport(resource, wire)) {
                imported.computeIfAbsent(packageName(wire.getCapability()), k -> new LinkedHashSet<>())
                        .add(wire.getCapability());
            }
        }
        imported.forEach((name, caps) -> {
            Set<Capability> sources = new LinkedHashSet<>();
            caps.forEach(c -> sources.addAll(sourcesOf(c, new HashSet<>())));
            if (caps.size() > 1 && !allCompatible(caps, sources)) {
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
        Set<Capability> pending = new LinkedHashSet<>(sourcesOf(root, new HashSet<>()));
        while (!pending.isEmpty()) {
            Capability current = pending.iterator().next();
            pending.remove(current);
            if (!visited.add(current) || current.getResource().equals(consumer)) {
                continue;
            }
            for (String used : uses(current)) {
                Set<Capability> implied = impliedSources(current.getResource(), used);
                for (Set<Capability> actual : actualSources(consumer, used)) {
                    if (!implied.isEmpty() && !actual.isEmpty() && !compatible(implied, actual)) {
                        violations.add(describe(consumer) + " sees package " + used + " from " + describe(actual)
                                + " but " + reason + " from " + describe(root.getResource()) + " implies "
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
                keys.add(Arrays.asList(capability.getResource(), packageName(capability)));
            } else {
                keys.add(capability);
            }
        }
        return keys;
    }

    private boolean allCompatible(Set<Capability> caps, Set<Capability> sources) {
        Set<Capability> first = sourcesOf(caps.iterator().next(), new HashSet<>());
        for (Capability cap : caps) {
            if (!compatible(first, sourcesOf(cap, new HashSet<>()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The capabilities a package capability really consists of: itself plus
     * what its bundle gets for the same package through Require-Bundle (split
     * packages), or, if the bundle substitutes the export, what it imports.
     */
    private Set<Capability> sourcesOf(Capability capability, Set<Capability> visiting) {
        Set<Capability> sources = new LinkedHashSet<>();
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
            sources.add(capability);
        } else if (visiting.add(capability)) {
            Resource exporter = capability.getResource();
            String name = packageName(capability);
            Set<Capability> imported = importedCapabilities(exporter, name);
            if (!imported.isEmpty()) {
                imported.forEach(c -> sources.addAll(sourcesOf(c, visiting)));
            } else {
                sources.add(capability);
                Set<Capability> required = new LinkedHashSet<>();
                for (Wire wire : wires(exporter)) {
                    if (isBundleWire(wire)) {
                        requiredExports(wire.getProvider(), name, required, new HashSet<>());
                    }
                }
                required.forEach(c -> sources.addAll(sourcesOf(c, visiting)));
            }
        }
        return sources;
    }

    private Set<Capability> importedCapabilities(Resource resource, String packageName) {
        Set<Capability> imported = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isImport(resource, wire) && packageName.equals(packageName(wire.getCapability()))) {
                imported.add(wire.getCapability());
            }
        }
        return imported;
    }

    private Set<Capability> ownExports(Resource resource, String packageName) {
        Set<Capability> own = new LinkedHashSet<>();
        if (importedCapabilities(resource, packageName).isEmpty()) {
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (packageName.equals(packageName(capability))) {
                    own.add(capability);
                }
            }
        }
        return own;
    }

    private Set<Capability> requiredCapabilities(Resource resource, String packageName) {
        Set<Capability> required = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isBundleWire(wire)) {
                requiredExports(wire.getProvider(), packageName, required, new HashSet<>());
            }
        }
        return required;
    }

    private Set<Capability> union(Set<Capability> capabilities) {
        Set<Capability> sources = new LinkedHashSet<>();
        capabilities.forEach(c -> sources.addAll(sourcesOf(c, new HashSet<>())));
        return sources;
    }

    /** What a resource provides for a package to whoever uses it: its export, else required, else imported. */
    private Set<Capability> impliedSources(Resource resource, String packageName) {
        Set<Capability> capabilities = ownExports(resource, packageName);
        if (capabilities.isEmpty()) {
            capabilities = requiredCapabilities(resource, packageName);
        }
        if (capabilities.isEmpty()) {
            capabilities = importedCapabilities(resource, packageName);
        }
        return union(capabilities);
    }

    /** The separately checked views of a package a resource has: through its export and through imports or required bundles. */
    private List<Set<Capability>> actualSources(Resource resource, String packageName) {
        List<Set<Capability>> views = new ArrayList<>();
        Set<Capability> own = ownExports(resource, packageName);
        if (!own.isEmpty()) {
            views.add(union(own));
        }
        Set<Capability> other = importedCapabilities(resource, packageName);
        if (other.isEmpty()) {
            other = requiredCapabilities(resource, packageName);
        }
        if (!other.isEmpty()) {
            views.add(union(other));
        }
        return views;
    }

    /** The packages the resource can see. */
    private Set<String> visiblePackages(Resource resource) {
        Set<String> names = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isImport(resource, wire)) {
                names.add(packageName(wire.getCapability()));
            } else if (isBundleWire(wire)) {
                exportedThroughBundle(wire.getProvider(), names, new HashSet<>());
            }
        }
        for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
            names.add(packageName(capability));
        }
        return names;
    }

    private void exportedThroughBundle(Resource required, Set<String> names, Set<Resource> visited) {
        if (visited.add(required)) {
            for (Capability capability : required.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                names.add(packageName(capability));
            }
            for (Wire wire : wires(required)) {
                if (isReexport(wire)) {
                    exportedThroughBundle(wire.getProvider(), names, visited);
                }
            }
        }
    }

    /** The capabilities that make the package visible to the resource (not yet reduced to their origin). */
    private Set<Capability> providersSeenBy(Resource resource, String packageName) {
        Set<Capability> providers = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isImport(resource, wire) && packageName.equals(packageName(wire.getCapability()))) {
                providers.add(wire.getCapability());
            }
        }
        if (providers.isEmpty()) {
            for (Wire wire : wires(resource)) {
                if (isBundleWire(wire)) {
                    requiredExports(wire.getProvider(), packageName, providers, new HashSet<>());
                }
            }
        }
        if (providers.isEmpty()) {
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (packageName.equals(packageName(capability))) {
                    providers.add(capability);
                }
            }
        }
        return providers;
    }

    private void requiredExports(Resource required, String packageName, Set<Capability> providers,
            Set<Resource> visited) {
        if (visited.add(required)) {
            for (Capability capability : required.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (packageName.equals(packageName(capability))) {
                    providers.add(capability);
                }
            }
            for (Wire wire : wires(required)) {
                if (isReexport(wire)) {
                    requiredExports(wire.getProvider(), packageName, providers, visited);
                }
            }
        }
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

    private boolean isImport(Resource resource, Wire wire) {
        return PackageNamespace.PACKAGE_NAMESPACE.equals(wire.getCapability().getNamespace())
                && !wire.getProvider().equals(resource);
    }

    private boolean isBundleWire(Wire wire) {
        return BundleNamespace.BUNDLE_NAMESPACE.equals(wire.getCapability().getNamespace());
    }

    private boolean isReexport(Wire wire) {
        return isBundleWire(wire) && BundleNamespace.VISIBILITY_REEXPORT
                .equals(wire.getRequirement().getDirectives().get(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE));
    }

    private String packageName(Capability capability) {
        return (String) capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
    }

    private List<Wire> wires(Resource resource) {
        return result.getOrDefault(resource, Collections.emptyList());
    }

    private String describe(Resource resource) {
        return resource.getCapabilities("osgi.identity").isEmpty() ? resource.toString()
                : String.valueOf(resource.getCapabilities("osgi.identity").get(0).getAttributes().get("osgi.identity"));
    }

    private String describe(Set<Capability> capabilities) {
        List<String> names = new ArrayList<>();
        capabilities.forEach(c -> names.add(describe(c.getResource())));
        return names.toString();
    }
}
