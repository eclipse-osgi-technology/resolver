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

import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;

/**
 * Lets a {@link UsesConflictStrategy} resolve the class space conflicts the
 * rules detect and records which exports are kept or discarded as a result.
 */
public final class ConflictPhase implements ResolverPhase<Void> {

    private final UsesConflictStrategy strategy;
    private final ResolverGraph graph;
    private final ClassSpace classSpace;

    /**
     * @param strategy   decides the conflicts
     * @param graph      the graph after the selection
     * @param classSpace the class space of the graph
     */
    public ConflictPhase(UsesConflictStrategy strategy, ResolverGraph graph, ClassSpace classSpace) {
        this.strategy = strategy;
        this.graph = graph;
        this.classSpace = classSpace;
    }

    /**
     * Runs the strategy on a {@link ResolverUsesSession} and explains the
     * substitutions of the outcome.
     */
    @Override
    public Void run() {
        strategy.enforceUses(new ResolverUsesSession(graph, classSpace));
        classSpace.explainSubstitutions();
        return null;
    }
}
