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
import java.util.function.Supplier;

import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.failures.UnsatisfiedRequirementException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
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
 * A fragment is attached wherever it can be. If a class space is inconsistent
 * because of a fragment that was not asked for explicitly, the fragment is not
 * attached to that host and the phases run again with a new strategy, see
 * {@link FragmentBlame}. Every round excludes at least one fragment from a
 * host, so the number of rounds is bounded by the attachments.
 */
public final class ResolveOperation {

    private final ResolveContext context;
    private final Supplier<UsesConflictStrategy> strategies;
    private ResolverGraph graph = new ResolverGraph();

    /**
     * @param context    the resolve context to resolve for
     * @param strategies creates the strategy that decides the class space
     *                   conflicts, once for every round of the resolve
     */
    public ResolveOperation(ResolveContext context, Supplier<UsesConflictStrategy> strategies) {
        this.context = context;
        this.strategies = strategies;
    }

    /**
     * The graph of the operation, it is filled by {@link #resolve()} and keeps
     * the state of every decision, so it is available for an explanation after
     * a failure as well. It is the graph of the last round.
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
        ExcludedFragments excluded = new ExcludedFragments();
        do {
            graph = new ResolverGraph();
            new DiscoveryPhase(context, graph).run();
            FragmentAttachmentPhase fragments = new FragmentAttachmentPhase(context, graph, excluded);
            fragments.run();
            ClassSpace classSpace = new ClassSpacePhase(graph).run();
            new ResolvabilityPhase(graph).run();
            fragments.attachSelectedFragments();
            new ConflictPhase(strategies.get(), graph, classSpace).run();
        } while (FragmentBlame.blame(graph, excluded));
        for (ResolverResource resource : graph.getMandatory()) {
            if (resource.getState() == ResolveState.FAILED) {
                ResolutionException failure = FailurePhase.exceptionFor(resource);
                if (failure instanceof UsesConstraintViolationException) {
                    Resource host = ((UsesConstraintViolationException) failure).getResource();
                    throw excluded.conflictOf(host).map(ResolutionException.class::cast).orElse(failure);
                }
                if (failure instanceof UnsatisfiedRequirementException) {
                    // a fragment that was left out because of its host fails with that conflict
                    Resource owner = ((UnsatisfiedRequirementException) failure).getRequirement().getResource();
                    throw excluded.conflictOf(owner).map(ResolutionException.class::cast)
                            .orElseGet(() -> excluded.causeOf(owner).orElse(failure));
                }
                throw failure;
            }
        }
        return new WiringPhase(graph).run();
    }
}
