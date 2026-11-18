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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.diagnostics.Diagnostics;
import org.eclipse.osgi.technology.resolver.diagnostics.DiagnosticsListener;
import org.eclipse.osgi.technology.resolver.failures.UnsatisfiedRequirementException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

/**
 * A resolver that runs the phases in the order they have to run and lets a
 * {@link UsesConflictStrategy} decide the class space conflicts the rules leave
 * open, and publishes the diagnostics of every outcome to a context that is
 * interested.
 * <ol>
 * <li>{@link DiscoveryPhase} builds the graph from the resolve context.</li>
 * <li>{@link FragmentAttachmentPhase} decides which fragments are attached
 * and where their capabilities are provided, once the candidates are
 * selected the resolver attaches the selected fragments to their hosts.</li>
 * <li>{@link ClassSpacePhase} builds the indices and rules.</li>
 * <li>{@link ResolvabilityPhase} decides which resources can be resolved and
 * selects the candidates to wire.</li>
 * <li>{@link ConflictPhase} lets a {@link UsesConflictStrategy} resolve the
 * class space conflicts the rules detect.</li>
 * <li>{@link FailurePhase} and {@link WiringPhase} produce the result.</li>
 * </ol>
 * A fragment is attached wherever it can be. If a class space is inconsistent
 * because of a fragment that was not asked for explicitly, the fragment is not
 * attached to that host and the phases run again with a new strategy, see
 * {@link FragmentBlame}. Every round excludes at least one fragment from a
 * host, so the number of rounds is bounded by the attachments.
 * A resolver with a decision procedure is a subclass that creates the strategy,
 * and it can replace a phase by overriding its {@code create...Phase} method.
 */
public abstract class PhasedResolver implements Resolver {

    /**
     * Creates the strategy for a resolve. It may depend on the context, and is
     * created for every round of a resolve, a round runs again if a fragment is
     * left out, so a strategy can keep state for the round it is asked in.
     *
     * @param context the context that is resolved
     * @return the strategy that resolves the detected class space
     *         conflicts
     */
    protected abstract UsesConflictStrategy createStrategy(ResolveContext context);

    /**
     * Creates the phase that builds the graph.
     *
     * @param context the context that is resolved
     * @param graph the graph of the current round
     * @return the phase
     */
    protected ResolverPhase<Void> createDiscoveryPhase(ResolveContext context, ResolverGraph graph) {
        return new DiscoveryPhase(context, graph);
    }

    /**
     * Creates the phase that attaches the fragments.
     *
     * @param context the context that is resolved
     * @param graph the graph of the current round
     * @param excluded the fragments that must not be attached to a host
     * @return the phase
     */
    protected ResolverPhase<Void> createFragmentAttachmentPhase(ResolveContext context, ResolverGraph graph,
            FragmentExclusions excluded) {
        return new FragmentAttachmentPhase(context, graph, excluded);
    }

    /**
     * Creates the phase that builds the indices and rules.
     *
     * @param graph the graph of the current round
     * @return the phase
     */
    protected ResolverPhase<ClassSpace> createClassSpacePhase(ResolverGraph graph) {
        return new ClassSpacePhase(graph);
    }

    /**
     * Creates the phase that decides which resources can be resolved.
     *
     * @param graph the graph of the current round
     * @return the phase
     */
    protected ResolverPhase<Void> createResolvabilityPhase(ResolverGraph graph) {
        return new ResolvabilityPhase(graph);
    }

    /**
     * Creates the phase that decides the class space conflicts with a new
     * strategy.
     *
     * @param context the context that is resolved
     * @param graph the graph of the current round
     * @param classSpace the indices and rules of the round
     * @return the phase
     */
    protected ResolverPhase<Void> createConflictPhase(ResolveContext context, ResolverGraph graph, ClassSpace classSpace) {
        return new ConflictPhase(createStrategy(context), graph, classSpace);
    }

    /**
     * Creates the phase that produces the wires.
     *
     * @param graph the graph of the last round
     * @return the phase
     */
    protected ResolverPhase<Map<Resource, List<Wire>>> createWiringPhase(ResolverGraph graph) {
        return new WiringPhase(graph);
    }

    @Override
    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        ExcludedFragments excluded = new ExcludedFragments();
        ResolverGraph graph = new ResolverGraph();
        try {
            do {
                graph = new ResolverGraph();
                createDiscoveryPhase(context, graph).run();
                createFragmentAttachmentPhase(context, graph, excluded).run();
                ClassSpace classSpace = createClassSpacePhase(graph).run();
                createResolvabilityPhase(graph).run();
                attachSelectedFragments(graph);
                createConflictPhase(context, graph, classSpace).run();
            } while (FragmentBlame.blame(graph, excluded));
            for (ResolverResource resource : graph.getMandatory()) {
                if (resource.getState() == ResolveState.FAILED) {
                    throw failure(FailurePhase.exceptionFor(resource), excluded);
                }
            }
            return createWiringPhase(graph).run();
        } finally {
            publish(context, new ResolverDiagnostics(graph.getResourceMap()));
        }
    }

    /**
     * After the selection of the candidates a fragment that is resolved
     * contributes its imports to the class space of the hosts it is attached
     * to.
     */
    private static void attachSelectedFragments(ResolverGraph graph) {
        Map<ResolverResource, List<ResolverResource>> byHost = new IdentityHashMap<>();
        for (ResolverResource fragment : graph.getResources()) {
            if (fragment.isFragment() && fragment.getState() == ResolveState.RESOLVED) {
                for (ResolverResource host : fragment.getHosts()) {
                    byHost.computeIfAbsent(host, k -> new ArrayList<>()).add(fragment);
                }
            }
        }
        byHost.forEach(ResolverResource::setAttachedFragments);
    }

    /**
     * A failure caused by a fragment that was asked for or left out is
     * reported with the conflict of the host.
     */
    private static ResolutionException failure(ResolutionException failure, ExcludedFragments excluded) {
        if (failure instanceof UsesConstraintViolationException) {
            Resource host = ((UsesConstraintViolationException) failure).getResource();
            return excluded.conflictOf(host).map(ResolutionException.class::cast).orElse(failure);
        }
        if (failure instanceof UnsatisfiedRequirementException) {
            Resource owner = ((UnsatisfiedRequirementException) failure).getRequirement().getResource();
            return excluded.conflictOf(owner).map(ResolutionException.class::cast)
                    .orElseGet(() -> excluded.causeOf(owner).orElse(failure));
        }
        return failure;
    }

    @Override
    public Map<Resource, List<Wire>> resolveDynamic(ResolveContext context, Wiring hostWiring,
            Requirement dynamicRequirement) throws ResolutionException {
        try {
            throw new ResolutionException("Dynamic resolving is not yet implemented");
        } finally {
            publish(context, new ResolverDiagnostics(Collections.<Resource, ResolverResource>emptyMap()));
        }
    }

    /**
     * Diagnostics are published for every outcome, but only if the context is
     * interested.
     */
    private static void publish(ResolveContext context, Diagnostics diagnostics) {
        if (context instanceof DiagnosticsListener) {
            ((DiagnosticsListener) context).publishDiagnostic(diagnostics);
        }
    }
}
