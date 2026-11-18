/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.phases;

/**
 * One step of a resolve that works on the graph of the current round. The
 * phases run in the order {@link PhasedResolver} lists them, a phase that
 * decides something records it in the graph and one that produces a result
 * returns it.
 *
 * @param <R> what the phase produces, {@link Void} if it only changes the graph
 */
public interface ResolverPhase<R> {

    /**
     * Runs the phase.
     *
     * @return the result of the phase, {@code null} for a phase that only
     *         changes the graph
     */
    R run();
}
