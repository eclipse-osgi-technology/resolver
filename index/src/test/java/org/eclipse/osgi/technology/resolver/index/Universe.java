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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;

/**
 * Bundles described by name, in which every capability that matches a
 * requirement is a candidate for it.
 * Bundles are described first, the resources are created when the first of
 * them is asked for.
 */
final class Universe {

    private final Map<String, ResourceBuilder> builders = new LinkedHashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    /** Starts the description of a bundle. */
    ResourceBuilder bundle(String name) {
        return builders.computeIfAbsent(name, n -> new ResourceBuilder());
    }

    Resource resource(String name) {
        if (resources.isEmpty()) {
            builders.forEach((n, builder) -> resources.put(n, builder.build()));
        }
        return resources.get(name);
    }

    List<Resource> resources() {
        resource(null);
        return new ArrayList<>(resources.values());
    }

    /** The capabilities of all bundles that match a requirement. */
    CandidateSource candidates() {
        return new CandidateSource() {
            @Override
            public List<? extends Capability> getCandidates(Requirement requirement) {
                List<Capability> result = new ArrayList<>();
                for (Resource resource : resources()) {
                    for (Capability capability : resource.getCapabilities(requirement.getNamespace())) {
                        if (ResourceUtils.matches(requirement, capability)) {
                            result.add(capability);
                        }
                    }
                }
                return result;
            }
        };
    }
}
