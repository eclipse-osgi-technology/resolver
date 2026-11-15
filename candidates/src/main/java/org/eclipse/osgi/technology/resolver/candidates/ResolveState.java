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

/**
 * Where a resource is in the resolution.
 */
public enum ResolveState {

    /**
     * Nothing found against it yet, assumed to be resolvable.
     */
    PENDING,

    /**
     * Can be resolved.
     */
    RESOLVED,

    /**
     * Can not be resolved, see {@link ResolverResource#getFailedRequirement()}
     * for the reason.
     */
    FAILED
}
