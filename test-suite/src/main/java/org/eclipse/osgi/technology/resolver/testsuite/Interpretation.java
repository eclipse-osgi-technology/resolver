/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.testsuite;

/**
 * How a resolver relates to a proposed interpretation of the specification,
 * see {@link ResolverEngine#assumeValid(String)}.
 */
public enum Interpretation {

    /**
     * The resolver does not follow the interpretation, the scenarios that rely
     * on it are skipped.
     */
    DENY,

    /**
     * The resolver follows the interpretation, only valid for a proposal with
     * one outcome. A scenario that asks for an option fails for this answer.
     */
    ACCEPT,

    /**
     * The proposal names two valid outcomes and the resolver produces the
     * first one.
     */
    OPTION_A,

    /**
     * The proposal names two valid outcomes and the resolver produces the
     * second one.
     */
    OPTION_B;
}
