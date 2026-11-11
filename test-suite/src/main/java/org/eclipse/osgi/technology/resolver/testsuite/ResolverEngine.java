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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * The resolver under test, registered by each test harness through
 * {@link java.util.ServiceLoader}. The scenarios only use this abstraction, so
 * they can run against any resolver on the classpath.
 * <p>
 * The specification does not mandate how a resolver reports why it failed, so
 * the scenarios state what is semantically expected and every engine asserts it
 * as far as it can tell, see the {@code assert...} methods.
 */
public interface ResolverEngine {

    /**
     * Returns the name of the resolver, used in messages.
     *
     * @return the name
     */
    String name();

    /**
     * Creates the context for a resolve. Engines that need more than a plain
     * context provide their own.
     *
     * @param available the resources the resolver can choose from
     * @param mandatory the resources that have to be resolved
     * @param optional  the resources that are resolved if possible
     * @return the context
     */
    default ResolveContext createContext(Collection<Resource> available, Collection<Resource> mandatory,
            Collection<Resource> optional) {
        return new DefaultResolveContext(available, mandatory, optional);
    }

    /**
     * Resolves the resources of the context.
     *
     * @param context the context
     * @return the wires of every resolved resource
     * @throws ResolutionException if the resolve fails
     */
    Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException;

    /**
     * Asserts that a failed resolve is caused by a requirement of a resource that
     * no capability satisfies.
     *
     * @param failure   the failure of the resolve
     * @param resource  the resource with the unsatisfied requirement
     * @param namespace the namespace of the requirement
     * @param name      the value of the primary attribute the requirement asks
     *                  for, {@code null} if not to be checked
     */
    void assertUnsatisfiedRequirement(ResolutionException failure, Resource resource, String namespace, String name);
}
