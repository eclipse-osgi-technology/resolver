/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.sat4j;

import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;

/**
 * A viewer that sees a capability which uses a package: whatever the exporter of the capability sees of that package
 * directly, the viewer sees implied, and the two views have to agree.
 */
final class SeenUse {

    final ResolverResource viewer;
    final ResolverCapability capability;
    /**
     * The literal that is true if the viewer sees the capability.
     */
    final int sees;

    SeenUse(ResolverResource viewer, ResolverCapability capability, int sees) {
        this.viewer = viewer;
        this.capability = capability;
        this.sees = sees;
    }
}
