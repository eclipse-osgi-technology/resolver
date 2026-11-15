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

import java.util.LinkedHashMap;
import java.util.Map;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * Bundles described by name and the candidates offered between them, to be
 * inspected through the wrappers of the candidates module.
 * Bundles are described first, the graph is created when the first of them is
 * asked for.
 */
final class Graph {

    private final Map<String, ResourceBuilder> builders = new LinkedHashMap<>();
    private final Map<String, ResolverResource> resources = new LinkedHashMap<>();

    /**
     * Starts the description of a bundle that can be required and hosts
     * fragments.
     */
    ResourceBuilder bundle(String name) {
        if (builders.containsKey(name)) {
            return builders.get(name);
        }
        ResourceBuilder builder = new ResourceBuilder();
        builder.addCapability(new CapReqBuilder("osgi.identity").addAttribute("osgi.identity", name));
        builder.addCapability(
                new CapReqBuilder(BundleNamespace.BUNDLE_NAMESPACE).addAttribute(BundleNamespace.BUNDLE_NAMESPACE, name));
        builder.addCapability(
                new CapReqBuilder(HostNamespace.HOST_NAMESPACE).addAttribute(HostNamespace.HOST_NAMESPACE, name));
        builders.put(name, builder);
        return builder;
    }

    /** Starts the description of a fragment of a host. */
    ResourceBuilder fragment(String name, String host) {
        if (builders.containsKey(name)) {
            return builders.get(name);
        }
        ResourceBuilder builder = new ResourceBuilder();
        builder.addCapability(new CapReqBuilder("osgi.identity").addAttribute("osgi.identity", name));
        builder.addFragmentHost(host, new Attrs());
        builders.put(name, builder);
        return builder;
    }

    ResolverResource resource(String name) {
        if (resources.isEmpty()) {
            builders.forEach((n, builder) -> resources.put(n, new ResolverResource(builder.build())));
        }
        return resources.get(name);
    }

    /**
     * Offers a capability of the provider for the requirement of the requirer
     * that asks for the name in the namespace, the namespace is the short form
     * of a package, bundle or host.
     */
    Candidate offer(String requirer, String namespace, String name, String provider) {
        return offer(requirer, namespace, name, provider, null);
    }

    /**
     * Like {@link #offer(String, String, String, String)}, the requirement is
     * the one whose filter contains the text.
     */
    Candidate offer(String requirer, String namespace, String name, String provider, String filterPart) {
        String full = full(namespace);
        ResolverRequirement requirement = null;
        for (ResolverRequirement candidate : resource(requirer).getResolverRequirements()) {
            String filter = candidate.getDirectives().get("filter");
            if (full.equals(candidate.getNamespace()) && filter.contains("(" + full + "=" + name + ")")
                    && (filterPart == null || filter.contains(filterPart))) {
                requirement = candidate;
            }
        }
        if (requirement == null) {
            throw new IllegalArgumentException("No " + full + "=" + name + " requirement of " + requirer);
        }
        return requirement.addCandidate(capability(provider, namespace, name));
    }

    /** The capability of the provider in the namespace with the name. */
    ResolverCapability capability(String provider, String namespace, String name) {
        String full = full(namespace);
        for (ResolverCapability candidate : resource(provider).getResolverCapabilities()) {
            if (full.equals(candidate.getNamespace()) && name.equals(candidate.getAttributes().get(full))) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No " + full + "=" + name + " capability of " + provider);
    }

    /** Offers a capability and selects it. */
    Candidate select(String requirer, String namespace, String name, String provider) {
        Candidate candidate = offer(requirer, namespace, name, provider);
        candidate.select("selected by the test");
        return candidate;
    }

    private static String full(String namespace) {
        switch (namespace) {
        case "package":
            return org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;
        case "bundle":
            return BundleNamespace.BUNDLE_NAMESPACE;
        case "host":
            return HostNamespace.HOST_NAMESPACE;
        default:
            return namespace;
        }
    }
}
