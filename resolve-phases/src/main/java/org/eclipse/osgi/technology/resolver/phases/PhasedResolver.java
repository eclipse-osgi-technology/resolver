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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.diagnostics.Diagnostics;
import org.eclipse.osgi.technology.resolver.diagnostics.DiagnosticsListener;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

/**
 * A resolver that runs the phases of a {@link ResolveOperation} and lets a
 * {@link UsesConflictStrategy} decide the class space conflicts the rules leave
 * open, and publishes the diagnostics of every outcome to a context that is
 * interested. A resolver with a decision procedure is a subclass that creates
 * the strategy.
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

    @Override
    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        ResolveOperation operation = new ResolveOperation(context, () -> createStrategy(context));
        try {
            return operation.resolve();
        } finally {
            publish(context, new ResolverDiagnostics(operation.getGraph().getResourceMap()));
        }
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
