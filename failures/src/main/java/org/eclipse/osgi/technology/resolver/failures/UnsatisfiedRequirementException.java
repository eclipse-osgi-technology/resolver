/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.failures;

import java.util.Collections;

import org.osgi.resource.Requirement;
import org.osgi.service.resolver.ResolutionException;

/**
 * A mandatory requirement has no candidate capability at all.
 * This is the root cause, not a bundle that fails only because it depends on another failing bundle.
 */
public class UnsatisfiedRequirementException extends ResolutionException {

    private static final long serialVersionUID = 1L;

    private final transient Requirement requirement;

    public UnsatisfiedRequirementException(Requirement requirement) {
        this(requirement, "Unsatisfied requirement " + requirement);
    }

    protected UnsatisfiedRequirementException(Requirement requirement, String message) {
        super(message, null, Collections.singleton(requirement));
        this.requirement = requirement;
    }

    /** The requirement without any candidate, its resource is the one that can not be resolved. */
    public Requirement getRequirement() {
        return requirement;
    }
}
