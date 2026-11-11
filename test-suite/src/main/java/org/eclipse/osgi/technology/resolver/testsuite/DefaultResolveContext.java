/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.testsuite;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.osgi.technology.resolver.index.CapabilityIndex;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;

import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.version.Version;

/**
 * The resolve context of the scenarios, a plain one: all given resources are
 * available and nothing is resolved yet. Providers are found by namespace and
 * requirement filter and ordered by highest version first, as a framework
 * would do. Capabilities are indexed lazily, so the set of available resources
 * must not change after the first lookup.
 */
public class DefaultResolveContext extends ResolveContext {

    private final Collection<Resource> available;
    private final Collection<Resource> mandatory;
    private final Collection<Resource> optional;
    private final Map<String, CapabilityIndex> indexes = new ConcurrentHashMap<>();

    /**
     * Creates the context.
     *
     * @param available the resources the resolver can choose from
     * @param mandatory the resources that have to be resolved
     * @param optional  the resources that are resolved if possible
     */
    public DefaultResolveContext(Collection<Resource> available, Collection<Resource> mandatory,
            Collection<Resource> optional) {
        this.available = available;
        this.mandatory = mandatory;
        this.optional = optional;
    }

    @Override
    public Collection<Resource> getOptionalResources() {
        return optional;
    }

    @Override
    public Collection<Resource> getMandatoryResources() {
        return mandatory;
    }

    @Override
    public List<Capability> findProviders(Requirement requirement) {
        String namespace = requirement.getNamespace();
        List<Capability> result = indexes.computeIfAbsent(namespace, n -> new CapabilityIndex(n, available))
                .findProviders(requirement);
        result.sort(Comparator.comparing(DefaultResolveContext::version).reversed());
        return result;
    }

    /**
     * The version of a capability as defined by its namespace, a capability
     * without one has the lowest version.
     */
    private static Version version(Capability capability) {
        Version version = ResourceUtils.getVersion(capability);
        return version == null ? Version.LOWEST : version;
    }

    @Override
    public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
        int index = 0;
        while (index < capabilities.size()
                && version(capabilities.get(index)).compareTo(version(hostedCapability)) >= 0) {
            index++;
        }
        capabilities.add(index, hostedCapability);
        return index;
    }

    @Override
    public boolean isEffective(Requirement requirement) {
        String effective = requirement.getDirectives().get(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE);
        return effective == null || Namespace.EFFECTIVE_RESOLVE.equals(effective);
    }

    @Override
    public Map<Resource, Wiring> getWirings() {
        return Collections.emptyMap();
    }
}
