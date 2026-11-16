/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.diagnostics.Diagnostics;
import org.eclipse.osgi.technology.resolver.diagnostics.DiagnosticsListener;
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
 * A resolve context over a list of resources that records the diagnostics it
 * receives. Providers are offered with the highest version first.
 */
final class TestContext extends ResolveContext implements DiagnosticsListener {

    private final List<Resource> available;
    private final List<Resource> mandatory;
    private final List<Resource> optional;
    final List<Diagnostics> published = new ArrayList<>();

    TestContext(List<Resource> available, List<Resource> mandatory, List<Resource> optional) {
        this.available = available;
        this.mandatory = mandatory;
        this.optional = optional;
    }

    /**
     * A context in which everything is available and the given bundles are
     * mandatory.
     */
    static TestContext resolving(Resource... mandatory) {
        return new TestContext(new ArrayList<>(Arrays.asList(mandatory)),
                new ArrayList<>(Arrays.asList(mandatory)), new ArrayList<>());
    }

    /**
     * Makes more resources available to choose from.
     */
    TestContext offering(Resource... more) {
        available.addAll(Arrays.asList(more));
        return this;
    }

    /**
     * Adds resources that are resolved if possible.
     */
    TestContext optionally(Resource... more) {
        optional.addAll(Arrays.asList(more));
        available.addAll(Arrays.asList(more));
        return this;
    }

    @Override
    public Collection<Resource> getMandatoryResources() {
        return mandatory;
    }

    @Override
    public Collection<Resource> getOptionalResources() {
        return optional;
    }

    @Override
    public List<Capability> findProviders(Requirement requirement) {
        List<Capability> result = new ArrayList<>();
        for (Resource resource : available) {
            for (Capability capability : resource.getCapabilities(requirement.getNamespace())) {
                if (ResourceUtils.matches(requirement, capability)) {
                    result.add(capability);
                }
            }
        }
        result.sort(Comparator.comparing(TestContext::version).reversed());
        return result;
    }

    private static Version version(Capability capability) {
        Version version = ResourceUtils.getVersion(capability);
        return version == null ? Version.LOWEST : version;
    }

    @Override
    public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
        capabilities.add(hostedCapability);
        return capabilities.size() - 1;
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

    @Override
    public void publishDiagnostic(Diagnostics diagnostics) {
        published.add(diagnostics);
    }
}
