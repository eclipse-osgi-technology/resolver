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

import org.osgi.resource.Capability;
import org.osgi.service.resolver.HostedCapability;

/**
 * A {@link ResolverCapability} that a fragment declares and its host provides.
 * A fragment has no class loader, so the host is the provider and the fragment
 * only the declaring resource.
 */
public class HostedResolverCapability extends ResolverCapability implements HostedCapability {

    /**
     * @param host the resource that provides the capability
     * @param wrapped the capability to wrap
     * @param fragment the fragment that declares the capability
     */
    HostedResolverCapability(ResolverResource host, Capability wrapped, ResolverResource fragment) {
        super(host, wrapped, fragment);
    }

    @Override
    public Capability getDeclaredCapability() {
        return getDeclared();
    }
}
