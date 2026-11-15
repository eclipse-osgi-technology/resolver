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

import java.util.List;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * Tells the index which capabilities a resolver may ever choose from for a
 * requirement.
 */
public interface CandidateSource {

    /**
     * Looks up the capabilities that a resolver may choose from.
     *
     * @param requirement the requirement
     * @return all capabilities that can satisfy the requirement, {@code null}
     * if the requirement is not known
     */
    List<? extends Capability> getCandidates(Requirement requirement);
}
