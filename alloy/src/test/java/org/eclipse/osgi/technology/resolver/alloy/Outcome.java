/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

/**
 * The outcome a scenario expects from the resolve.
 */
enum Outcome {

    /** A valid resolution that satisfies all assertions exists. */
    SUCCEEDS,

    /** No valid resolution exists. */
    FAILS,

    /** No valid resolution exists, but one would without the uses constraint. */
    FAILS_BECAUSE_OF_USES
}
