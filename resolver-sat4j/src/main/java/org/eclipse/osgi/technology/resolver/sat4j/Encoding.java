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

/**
 * How much of the class space rule is handed to the solver.
 */
public enum Encoding {

    /**
     * Every package that can take part in a conflict is encoded, the solution is one of the whole problem and is
     * asked for once.
     */
    FULL,

    /**
     * Only the packages of the conflicts found and the packages whose class space reaches them are encoded.
     * The solution is tried on the graph and checked with the rule, if it is not consistent the packages of its
     * conflicts are added and it is solved again, so the solution is one of the whole problem as well.
     */
    SLICED
}
