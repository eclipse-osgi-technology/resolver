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

import java.util.Map;

import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.HostedCapability;

/**
 * A capability of a fragment as it is provided by the host it is attached to.
 */
class FragmentHostedCapability implements HostedCapability {

    private final Resource host;
    private final Capability declared;
    private final Map<String, Object> attributes;

    FragmentHostedCapability(Resource host, Capability declared, Map<String, Object> attributes) {
        this.host = host;
        this.declared = declared;
        this.attributes = attributes;
    }

    @Override
    public Capability getDeclaredCapability() {
        return declared;
    }

    @Override
    public String getNamespace() {
        return declared.getNamespace();
    }

    @Override
    public Map<String, String> getDirectives() {
        return declared.getDirectives();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Resource getResource() {
        return host;
    }

    @Override
    public String toString() {
        return declared + " hosted by " + host;
    }
}
