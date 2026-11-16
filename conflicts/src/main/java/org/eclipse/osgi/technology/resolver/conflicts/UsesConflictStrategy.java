/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.conflicts;

/**
 * Decides how the class space constraints are enforced after the providers are
 * selected: every resolved resource has to see each package from one provider
 * (the {@code uses} directive, Core specification 3.7.6 and 6.4.6), and what is
 * not consistent is either changed or failed. Everything up to the selection
 * and the explanation of the result are the same for every strategy, only the
 * decision procedure for resolving the detected conflicts differs.
 * <p>
 * A strategy may search for another selection one change at a time and re-check
 * what can see the change, hand the open choices to a constraint solver, or
 * combine both. It must leave the session in a state where every resource that
 * is still resolved has a consistent class space or has been failed through
 * {@link UsesSession#fail}, and failures must have been propagated with
 * {@link UsesSession#propagateFailures}, the session checks nothing after the
 * strategy returns. Every decision has to be recorded in the candidate graph
 * (the states and reasons of the candidates), as an explanation of the result
 * is derived from it.
 */
public interface UsesConflictStrategy {

    /**
     * Enforces the class space constraints of all resolved resources of the
     * session.
     *
     * @param session the state of the resolve after the selection
     */
    void enforceUses(UsesSession session);
}
