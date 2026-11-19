/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.sat4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.failures.ExportSubstitutedException;
import org.eclipse.osgi.technology.resolver.failures.UnsatisfiedRequirementException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
import org.eclipse.osgi.technology.resolver.testsuite.ResolverEngine;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * Runs the scenarios against the {@link Sat4jResolver}, which reports
 * failures with the specific exception types.
 */
public class Sat4jEngine implements ResolverEngine {

    @Override
    public String name() {
        return "sat4j";
    }

    @Override
    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        return new Sat4jResolver().resolve(context);
    }

    @Override
    public void assertUnsatisfiedRequirement(ResolutionException failure, Resource resource, String namespace,
            String name) {
        assertThat(failure).isInstanceOfSatisfying(UnsatisfiedRequirementException.class, e -> {
            Requirement requirement = e.getRequirement();
            assertThat(requirement.getResource()).isEqualTo(resource);
            assertThat(requirement.getNamespace()).isEqualTo(namespace);
            if (name != null) {
                assertThat(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                        .contains("(" + namespace + "=" + name + ")");
            }
            assertThat(e.getUnresolvedRequirements()).contains(requirement);
        });
    }

    @Override
    public void assertUsesConflict(ResolutionException failure, String packageName, Resource resource) {
        assertThat(failure).isInstanceOfSatisfying(UsesConstraintViolationException.class, e -> {
            assertThat(e.getPackageName()).isEqualTo(packageName);
            if (resource != null) {
                assertThat(e.getResource()).isEqualTo(resource);
            }
            assertThat(e.getConflictingCapabilities()).hasSizeGreaterThan(1);
        });
    }

    @Override
    public void assertImportHiddenBySubstitution(ResolutionException failure, Resource importer,
            String packageName) {
        assertThat(failure).isInstanceOfSatisfying(ExportSubstitutedException.class, e -> {
            assertThat(e.getRequirement().getResource()).isEqualTo(importer);
            assertThat(e.getRequirement().getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                    .contains("(" + PackageNamespace.PACKAGE_NAMESPACE + "=" + packageName + ")");
            assertThat(e.getExport()).isNotNull();
            assertThat(e.getSubstitute()).isNotNull();
        });
    }
}
