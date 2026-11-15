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
 * What became of a candidate capability for a requirement.
 */
public enum CandidateState {

    /**
     * Offered, no decision yet or not needed.
     */
    AVAILABLE,

    /**
     * Chosen to satisfy the requirement.
     */
    SELECTED,

    /**
     * Excluded for good, the {@link Candidate#getReason() reason} says why.
     */
    REJECTED,

    /**
     * Excluded only because another candidate is preferred, it is an
     * alternative if that one causes a conflict.
     */
    DEFERRED
}
