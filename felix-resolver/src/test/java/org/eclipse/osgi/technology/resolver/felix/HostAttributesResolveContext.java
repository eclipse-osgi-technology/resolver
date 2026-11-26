/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.felix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.testsuite.DefaultResolveContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.HostedCapability;

/**
 * A context for a resolver that leaves the host attributes to the context: the
 * framework associates the bundle-symbolic-name and bundle-version of the host
 * with a package a fragment exports, so such a package is a provider if one of
 * the hosts matches the requirement with its values, and not if only the
 * fragment would. A resolver that does this on its own needs no such context.
 */
public class HostAttributesResolveContext extends DefaultResolveContext {

    private final Collection<Resource> available;

    /**
     * Creates the context.
     *
     * @param available the resources the resolver can choose from
     * @param mandatory the resources that have to be resolved
     * @param optional  the resources that are resolved if possible
     */
    public HostAttributesResolveContext(Collection<Resource> available, Collection<Resource> mandatory,
            Collection<Resource> optional) {
        super(available, mandatory, optional);
        this.available = available;
    }

    @Override
    public List<Capability> findProviders(Requirement requirement) {
        List<Capability> result = super.findProviders(requirement);
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
            return result;
        }
        Filter filter = filter(requirement);
        result.removeIf(c -> hostRequirement(c.getResource()) != null);
        List<Capability> hosted = new ArrayList<>();
        for (Resource resource : available) {
            Requirement hostRequirement = hostRequirement(resource);
            if (hostRequirement == null) {
                continue;
            }
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (matchesHost(filter, capability, findProviders(hostRequirement))) {
                    hosted.add(capability);
                }
            }
        }
        result.addAll(hosted);
        result.sort(Comparator.comparing(DefaultResolveContext::version).reversed());
        return result;
    }

    /**
     * Orders the capabilities a fragment contributes to several hosts by the
     * version of the host, highest first, as the host is part of what a
     * requirement matched.
     */
    @Override
    public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
        int index = 0;
        while (index < capabilities.size() && compare(capabilities.get(index), hostedCapability) >= 0) {
            index++;
        }
        capabilities.add(index, hostedCapability);
        return index;
    }

    private static int compare(Capability existing, HostedCapability hosted) {
        int result = version(existing).compareTo(version(hosted));
        if (result != 0 || !(existing instanceof HostedCapability)) {
            return result;
        }
        return hostVersion(existing.getResource()).compareTo(hostVersion(hosted.getResource()));
    }

    private static Version hostVersion(Resource host) {
        List<Capability> identity = host.getCapabilities(
                IdentityNamespace.IDENTITY_NAMESPACE);
        Object version = identity.isEmpty() ? null
                : identity.get(0).getAttributes()
                        .get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        return version instanceof Version v ? v : Version.emptyVersion;
    }

    private static boolean matchesHost(Filter filter, Capability capability, List<Capability> hosts) {
        for (Capability host : hosts) {
            Map<String, Object> attributes = new HashMap<>(capability.getAttributes());
            attributes.put(PackageNamespace.CAPABILITY_BUNDLE_SYMBOLICNAME_ATTRIBUTE,
                    host.getAttributes().get(HostNamespace.HOST_NAMESPACE));
            attributes.put(PackageNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE,
                    host.getAttributes().get(HostNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE));
            if (filter == null || filter.matches(attributes)) {
                return true;
            }
        }
        return false;
    }

    private static Filter filter(Requirement requirement) {
        String text = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        try {
            return text == null ? null : FrameworkUtil.createFilter(text);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Requirement hostRequirement(Resource resource) {
        List<Requirement> requirements = resource.getRequirements(HostNamespace.HOST_NAMESPACE);
        return requirements.isEmpty() ? null : requirements.get(0);
    }
}
