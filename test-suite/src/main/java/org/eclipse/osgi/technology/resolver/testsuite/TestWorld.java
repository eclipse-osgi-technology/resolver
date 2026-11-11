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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

import aQute.bnd.osgi.resource.ResourceUtils;

/**
 * State of one scenario, shared between the step classes. It contains the
 * available bundles and starts the resolve operation. Starting a new bundle or
 * resolving implicitly completes the bundle that is currently being defined.
 */
public class TestWorld {

    private final List<Resource> available = new ArrayList<>();
    private final ResolverEngine engine = loadEngine();
    private BundleBuilder current;
    private ResolveContext context;

    private static ResolverEngine loadEngine() {
        return ServiceLoader.load(ResolverEngine.class).findFirst().orElseThrow(
                () -> new IllegalStateException("No ResolverEngine is registered, the test harness has to provide one"));
    }

    /**
     * Starts a new bundle and completes the previous one.
     *
     * @param ref the symbolic name and version of the bundle
     * @return the builder that describes the bundle
     */
    public BundleBuilder startBundle(BundleRef ref) {
        commit();
        current = new BundleBuilder(ref);
        return current;
    }

    private void commit() {
        if (current != null) {
            available.add(current.build());
            current = null;
        }
    }

    /**
     * Finds the one available bundle a reference points to.
     *
     * @param ref the reference
     * @return the resource of the bundle
     * @throws IllegalStateException if there is not exactly one such bundle
     */
    public Resource find(BundleRef ref) {
        List<Resource> matches = available.stream().filter(r -> matches(r, ref)).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + ref + " but found " + matches.size() + " (available: " + available + ")");
        }
        return matches.get(0);
    }

    private static boolean matches(Resource resource, BundleRef ref) {
        return ref.name().equals(ResourceUtils.getIdentity(resource)) && (ref.version() == null
                || ref.version().toString().equals(ResourceUtils.getVersion(resource).toString()));
    }

    /**
     * Resolves with all bundles available.
     *
     * @param mandatory the bundles that have to be resolved
     * @return the wires of every resolved resource
     * @throws ResolutionException if the resolve fails
     */
    public Map<Resource, List<Wire>> resolve(Collection<BundleRef> mandatory) throws ResolutionException {
        return resolve(mandatory, List.of());
    }

    /**
     * Resolves with all bundles available, some of them only if possible.
     *
     * @param mandatory the bundles that have to be resolved
     * @param optional  the bundles that are resolved if possible
     * @return the wires of every resolved resource
     * @throws ResolutionException if the resolve of the mandatory bundles fails
     */
    public Map<Resource, List<Wire>> resolve(Collection<BundleRef> mandatory, Collection<BundleRef> optional)
            throws ResolutionException {
        commit();
        context = engine.createContext(available, mandatory.stream().map(this::find).toList(),
                optional.stream().map(this::find).toList());
        return engine.resolve(context);
    }

    /**
     * Returns the resolver under test.
     *
     * @return the engine
     */
    public ResolverEngine engine() {
        return engine;
    }

    /**
     * Returns the resources the resolver was asked to resolve.
     *
     * @return the mandatory resources, empty before a resolve
     */
    public Collection<Resource> mandatoryResources() {
        return context == null ? List.of() : context.getMandatoryResources();
    }

    /**
     * Returns the resources the resolver may resolve in addition.
     *
     * @return the optional resources, empty before a resolve
     */
    public Collection<Resource> optionalResources() {
        return context == null ? List.of() : context.getOptionalResources();
    }
}
