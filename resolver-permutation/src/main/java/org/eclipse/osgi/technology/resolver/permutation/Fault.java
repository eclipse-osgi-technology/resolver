/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.permutation;

import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;

/**
 * A resource whose class space is not consistent for the choices of a permutation, and the violation that was found.
 * It is also the reason that is recorded for the candidates a permutation removes because of it.
 */
final class Fault {

    private final ResolverResource resource;
    private final UsesViolation violation;

    Fault(ResolverResource resource, UsesViolation violation) {
        this.resource = resource;
        this.violation = violation;
    }

    ResolverResource getResource() {
        return resource;
    }

    UsesViolation getViolation() {
        return violation;
    }

    /**
     * What is told as the cause of a replaced provider.
     */
    String describe() {
        if (violation == null) {
            return "class space";
        }
        return "class space of " + resource.getName() + " (package " + violation.getPackageName() + ")";
    }
}
