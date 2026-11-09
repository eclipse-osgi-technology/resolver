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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.WireImpl;

/**
 * A set of bundles and one explicit wiring of them, to be judged by an verifier.
 * Bundles are described first, the resources are created when the first of
 * them is asked for.
 */
final class Universe {

    private final Map<String, ResourceBuilder> builders = new LinkedHashMap<>();
    private final Map<String, Resource> bundles = new LinkedHashMap<>();
    private final Map<String, List<Wire>> wires = new LinkedHashMap<>();

    /** Starts the description of a bundle, which has the identity and bundle capabilities of its name. */
    ResourceBuilder bundle(String name) {
        ResourceBuilder builder = new ResourceBuilder();
        builder.addCapability(new CapReqBuilder("osgi.identity").addAttribute("osgi.identity", name));
        builder.addCapability(
                new CapReqBuilder(BundleNamespace.BUNDLE_NAMESPACE).addAttribute(BundleNamespace.BUNDLE_NAMESPACE, name));
        builders.put(name, builder);
        return builder;
    }

    /** The directive that lists the packages a capability uses. */
    static Attrs uses(String... packages) {
        return Attrs.create("uses:", String.join(",", packages));
    }

    /** Attributes of a generic capability with a value in its own namespace, optionally using packages. */
    static Attrs capability(String namespace, String value, String... packages) {
        Attrs attrs = packages.length == 0 ? new Attrs() : uses(packages);
        attrs.put(namespace, value);
        return attrs;
    }

    private Map<String, Resource> bundles() {
        if (bundles.isEmpty()) {
            builders.forEach((name, builder) -> bundles.put(name, builder.build()));
        }
        return bundles;
    }

    List<Resource> resources() {
        return new ArrayList<>(bundles().values());
    }

    Resource resource(String name) {
        return bundles().get(name);
    }

    /** Wires the requirement of a bundle for a package, bundle or capability name to the provider. */
    Universe wire(String requirer, String namespaceShort, String name, String provider) {
        return wire(requirer, namespaceShort, name, provider, -1);
    }

    /** Like {@link #wire(String, String, String, String)} for a provider that offers the name several times, index selects one. */
    Universe wire(String requirer, String namespaceShort, String name, String provider, int index) {
        String namespace = namespaceShort;
        if ("package".equals(namespaceShort)) {
            namespace = PackageNamespace.PACKAGE_NAMESPACE;
        } else if ("bundle".equals(namespaceShort)) {
            namespace = BundleNamespace.BUNDLE_NAMESPACE;
        }
        Resource from = resource(requirer);
        Requirement requirement = null;
        for (Requirement candidate : from.getRequirements(namespace)) {
            String filter = candidate.getDirectives().get("filter");
            boolean restricted = filter.contains("bundle-symbolic-name=");
            if (filter.contains("(" + namespace + "=" + name + ")")
                    && (!restricted || filter.contains("bundle-symbolic-name=" + provider + ")"))) {
                requirement = candidate;
            }
        }
        Capability capability = null;
        int position = 0;
        for (Capability candidate : resource(provider).getCapabilities(namespace)) {
            if (name.equals(candidate.getAttributes().get(namespace))) {
                if (index < 0 || index == position) {
                    capability = candidate;
                }
                position++;
            }
        }
        if (requirement == null || capability == null) {
            throw new IllegalArgumentException("Can not wire " + requirer + " " + namespace + "=" + name + " to " + provider);
        }
        wires.computeIfAbsent(requirer, k -> new ArrayList<>()).add(new WireImpl(capability, requirement));
        return this;
    }

    /** The explicit wiring, bundles without wires are part of it. */
    Map<Resource, List<Wire>> wiring() {
        Map<Resource, List<Wire>> result = new LinkedHashMap<>();
        bundles().forEach((name, resource) -> result.put(resource, wires.computeIfAbsent(name, k -> new ArrayList<>())));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        bundles().forEach((name, resource) -> {
            text.append("\n  ").append(name).append(':');
            for (Capability capability : resource.getCapabilities(null)) {
                if (!"osgi.identity".equals(capability.getNamespace())
                        && !BundleNamespace.BUNDLE_NAMESPACE.equals(capability.getNamespace())) {
                    text.append(" provides ").append(capability.getAttributes()).append(capability.getDirectives());
                }
            }
            for (Requirement requirement : resource.getRequirements(null)) {
                text.append(" requires ").append(requirement.getDirectives().get("filter"));
            }
        });
        return text.toString();
    }
}
