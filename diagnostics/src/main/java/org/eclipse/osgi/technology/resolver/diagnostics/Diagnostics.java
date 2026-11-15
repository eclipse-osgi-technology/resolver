/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.diagnostics;

import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The explanation a resolver gives for one resolve operation.
 * Instead of a fixed list of findings it is an object that can be queried, so
 * resolvers are free in how they keep their knowledge and further queries
 * (for example the reason for a single requirement) can be added step by step.
 */
@ProviderType
public interface Diagnostics {

    /**
     * Explains what the resolver decided for the given resource, for example
     * why it was resolved with certain wires or why it could not be resolved.
     *
     * @param resource a resource that took part in the resolve operation
     * @return a human readable explanation or empty if the resolver has nothing to say about the resource
     */
    Optional<String> getMessage(Resource resource);

    /**
     * Explains what the resolver decided for a single requirement of a resource, for example which of the
     * possible providers was chosen and why the others were not.
     *
     * @param requirement a requirement of a resource that took part in the resolve operation, its resource identifies the owner
     * @return a human readable explanation or empty if the resolver has nothing to say about the requirement
     */
    Optional<String> getMessage(Requirement requirement);

    /**
     * Explains what the resolver decided for a single capability of a resource, for example why an export
     * is not provided to others because the resource uses another provider for the same package instead.
     *
     * @param capability a capability of a resource that took part in the resolve operation, its resource identifies the owner
     * @return a human readable explanation or empty if the resolver has nothing to say about the capability
     */
    Optional<String> getMessage(Capability capability);
}
