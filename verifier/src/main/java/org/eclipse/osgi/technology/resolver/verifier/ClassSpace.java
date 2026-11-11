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

import java.util.Collections;
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
 * The class space of the resources of a resolve result, derived from the plain
 * wires only.
 * <p>
 * A resource sees a package either from its imports, from required bundles
 * (including the bundles they reexport) or from its own export, in that order
 * (OSGi Core 3.7.5 "Package Constraints").
 */
public final class ClassSpace {

    private final Map<Resource, List<Wire>> result;

    /**
     * Creates the class space of a result.
     *
     * @param result the wires of each resolved resource
     */
    public ClassSpace(Map<Resource, List<Wire>> result) {
        this.result = result;
    }

    /**
     * The names of the packages a resource sees through its imports, the
     * bundles it requires and its own exports.
     *
     * @param resource the resource
     * @return the package names
     */
    public Set<String> packages(Resource resource) {
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

    /**
     * The package capabilities that make a package visible to a resource: its
     * imports, else the exports of its required bundles, else its own exports.
     *
     * @param resource    the resource that sees the package
     * @param packageName the name of the package
     * @return the capabilities, empty if the package is not visible
     */
    public Set<Capability> providers(Resource resource, String packageName) {
        Set<Capability> providers = imported(resource, packageName);
        if (providers.isEmpty()) {
            providers = required(resource, packageName);
        }
        if (providers.isEmpty()) {
            providers = exported(resource, packageName);
        }
        return providers;
    }

    /**
     * The resources a resource really gets a package from. In contrast to
     * {@link #providers(Resource, String)} this follows an exporter that
     * substitutes its export with an import and adds what an exporter gets of
     * the same package through the bundles it requires (split packages).
     *
     * @param resource    the resource that sees the package
     * @param packageName the name of the package
     * @return the resources, empty if the package is not visible
     */
    public Set<Resource> sources(Resource resource, String packageName) {
        Set<Resource> sources = new LinkedHashSet<>();
        for (Capability provider : providers(resource, packageName)) {
            sourcesOf(provider, new HashSet<>()).forEach(c -> sources.add(c.getResource()));
        }
        return sources;
    }

    /**
     * The capabilities a capability really consists of: itself plus what its
     * resource gets for the same package through Require-Bundle (split
     * packages), or, if the resource substitutes the export, what it imports.
     * Capabilities of other namespaces consist of themselves.
     */
    Set<Capability> sourcesOf(Capability capability, Set<Capability> visiting) {
        Set<Capability> sources = new LinkedHashSet<>();
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
            sources.add(capability);
        } else if (visiting.add(capability)) {
            Resource exporter = capability.getResource();
            String name = packageName(capability);
            Set<Capability> imported = imported(exporter, name);
            if (!imported.isEmpty()) {
                imported.forEach(c -> sources.addAll(sourcesOf(c, visiting)));
            } else {
                sources.add(capability);
                required(exporter, name).forEach(c -> sources.addAll(sourcesOf(c, visiting)));
            }
        }
        return sources;
    }

    /** The package capabilities a resource imports. */
    Set<Capability> imported(Resource resource, String packageName) {
        Set<Capability> imported = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isImport(resource, wire) && packageName.equals(packageName(wire.getCapability()))) {
                imported.add(wire.getCapability());
            }
        }
        return imported;
    }

    /** The package capabilities a resource exports and does not substitute with an import. */
    Set<Capability> exported(Resource resource, String packageName) {
        Set<Capability> exported = new LinkedHashSet<>();
        if (imported(resource, packageName).isEmpty()) {
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (packageName.equals(packageName(capability))) {
                    exported.add(capability);
                }
            }
        }
        return exported;
    }

    /** The package capabilities the required bundles of a resource and their reexports export. */
    Set<Capability> required(Resource resource, String packageName) {
        Set<Capability> required = new LinkedHashSet<>();
        for (Wire wire : wires(resource)) {
            if (isBundleWire(wire)) {
                requiredExports(wire.getProvider(), packageName, required, new HashSet<>());
            }
        }
        return required;
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

    static boolean isImport(Resource resource, Wire wire) {
        return PackageNamespace.PACKAGE_NAMESPACE.equals(wire.getCapability().getNamespace())
                && !wire.getProvider().equals(resource);
    }

    static boolean isBundleWire(Wire wire) {
        return BundleNamespace.BUNDLE_NAMESPACE.equals(wire.getCapability().getNamespace());
    }

    private static boolean isReexport(Wire wire) {
        return isBundleWire(wire) && BundleNamespace.VISIBILITY_REEXPORT
                .equals(wire.getRequirement().getDirectives().get(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE));
    }

    static String packageName(Capability capability) {
        return (String) capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
    }

    List<Wire> wires(Resource resource) {
        return result.getOrDefault(resource, Collections.emptyList());
    }
}
