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

import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * One resolve operation: the phases in the order they have to run.
 * <ol>
 * <li>{@link DiscoveryPhase} builds the graph from the resolve context.</li>
 * <li>{@link FragmentAttachmentPhase} decides which fragments are attached and where
 * their capabilities are provided.</li>
 * <li>{@link ClassSpacePhase} builds the indices and rules.</li>
 * <li>{@link ResolvabilityPhase} decides which resources can be resolved and selects
 * the candidates to wire.</li>
 * <li>{@link ConflictPhase} lets a {@link UsesConflictStrategy} resolve the class
 * space conflicts the rules detect.</li>
 * <li>{@link FailurePhase} and {@link WiringPhase} produce the result.</li>
 * </ol>
 */
public final class ResolveOperation {

    private final ResolveContext context;
    private final UsesConflictStrategy strategy;
    private final ResolverGraph graph = new ResolverGraph();

    /**
     * @param context  the resolve context to resolve for
     * @param strategy the strategy that decides the class space conflicts
     */
    public ResolveOperation(ResolveContext context, UsesConflictStrategy strategy) {
        this.context = context;
        this.strategy = strategy;
    }

    /**
     * The graph of the operation, it is filled by {@link #resolve()} and keeps
     * the state of every decision, so it is available for an explanation after
     * a failure as well.
     *
     * @return the graph
     */
    public ResolverGraph getGraph() {
        return graph;
    }

    /**
     * Runs the phases.
     *
     * @return the wires of the resolved resources that are needed by the
     *       requested ones
     * @throws ResolutionException if a mandatory resource can not be resolved
     */
    public Map<Resource, List<Wire>> resolve() throws ResolutionException {
        new DiscoveryPhase(context, graph).run();
        FragmentAttachmentPhase fragments = new FragmentAttachmentPhase(context, graph);
        fragments.run();
        ClassSpace classSpace = new ClassSpacePhase(graph).run();
        new ResolvabilityPhase(graph).run();
        fragments.attachSelectedFragments();
        new ConflictPhase(strategy, graph, classSpace).run();
        for (ResolverResource resource : graph.getMandatory()) {
            if (resource.getState() == ResolveState.FAILED) {
                throw FailurePhase.exceptionFor(resource);
            }
        }
        return new WiringPhase(graph).run();
    }
}
