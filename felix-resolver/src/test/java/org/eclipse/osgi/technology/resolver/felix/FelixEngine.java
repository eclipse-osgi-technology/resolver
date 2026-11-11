/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.felix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.apache.felix.resolver.Logger;
import org.apache.felix.resolver.ResolverImpl;
import org.eclipse.osgi.technology.resolver.testsuite.ResolverEngine;
import org.osgi.resource.Namespace;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * Runs the scenarios against the Apache Felix resolver.
 */
public class FelixEngine implements ResolverEngine {

    private final ResolverImpl resolver = new ResolverImpl(new Logger(Logger.LOG_ERROR));

    @Override
    public String name() {
        return "felix";
    }

    @Override
    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        return resolver.resolve(context);
    }

    /**
     * Checks the unresolved requirements of the root of the cause chain of the
     * failure, as they name what could not be satisfied.
     */
    @Override
    public void assertUnsatisfiedRequirement(ResolutionException failure, Resource resource, String namespace,
            String name) {
        ResolutionException root = failure;
        while (root.getCause() instanceof ResolutionException cause && !cause.getUnresolvedRequirements().isEmpty()) {
            root = cause;
        }
        assertThat(root.getUnresolvedRequirements()).anySatisfy(requirement -> {
            assertThat(requirement.getResource()).isEqualTo(resource);
            assertThat(requirement.getNamespace()).isEqualTo(namespace);
            if (name != null) {
                assertThat(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                        .contains("(" + namespace + "=" + name + ")");
            }
        });
    }
}
