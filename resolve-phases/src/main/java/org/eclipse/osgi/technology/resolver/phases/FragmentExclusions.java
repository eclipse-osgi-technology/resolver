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

import java.util.Optional;

import org.osgi.resource.Resource;

/**
 * The fragments that must not be attached to a host, they outlive the graph of
 * one round of a resolve, so they refer to the resources the graph wraps.
 */
public interface FragmentExclusions {

    /**
     * Tells why a fragment is not attached to a host.
     *
     * @param fragment the fragment
     * @param host the host
     * @return the reason, empty if the fragment may be attached
     */
    Optional<String> reasonFor(Resource fragment, Resource host);
}
