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

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

/**
 * A wire of the result, it only refers to the objects the resolve context
 * handed out (or hosted views of them).
 */
class ResolvedWire implements Wire {

    private final Resource requirer;
    private final Requirement requirement;
    private final Resource provider;
    private final Capability capability;

    ResolvedWire(Resource requirer, Requirement requirement, Resource provider, Capability capability) {
        this.requirer = requirer;
        this.requirement = requirement;
        this.provider = provider;
        this.capability = capability;
    }

    @Override
    public Capability getCapability() {
        return capability;
    }

    @Override
    public Requirement getRequirement() {
        return requirement;
    }

    @Override
    public Resource getProvider() {
        return provider;
    }

    @Override
    public Resource getRequirer() {
        return requirer;
    }

    @Override
    public String toString() {
        return requirer + " -> " + capability + " of " + provider;
    }
}
