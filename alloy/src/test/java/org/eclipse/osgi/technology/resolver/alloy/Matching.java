/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.testsuite.DefaultResolveContext;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * Builds the resources of a scenario and relates their capabilities and
 * requirements to the atoms of the Alloy model. Which capability satisfies a
 * requirement is decided by the same resolve context as in the other
 * resolvers, the Alloy model only decides which of them are wired.
 */
class Matching {

    final DefaultResolveContext context;
    final Map<Capability, String> exports = new IdentityHashMap<>();
    final Map<Resource, String> bundles = new IdentityHashMap<>();
    private final Map<ImportSpec, Requirement> imports = new IdentityHashMap<>();
    private final Map<RequireSpec, Requirement> requires = new IdentityHashMap<>();

    Matching(AlloyWorld world) {
        List<Resource> available = new ArrayList<>();
        for (BundleSpec b : world.bundles) {
            Resource resource = b.builder.build();
            available.add(resource);
            bundles.put(resource, b.atom);
            // the builder adds one capability or requirement per step, in the order of the steps
            relate(b.exports, resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE), (e, c) -> exports.put(c, e.atom()));
            relate(b.imports, resource.getRequirements(PackageNamespace.PACKAGE_NAMESPACE), imports::put);
            relate(b.requires, resource.getRequirements(BundleNamespace.BUNDLE_NAMESPACE), requires::put);
        }
        context = new DefaultResolveContext(available, List.of(), List.of());
    }

    private static <S, T> void relate(List<S> specs, List<T> built, java.util.function.BiConsumer<S, T> into) {
        if (specs.size() != built.size()) {
            throw new IllegalStateException("The resource has " + built.size() + " entries for " + specs.size() + " steps");
        }
        for (int i = 0; i < specs.size(); i++) {
            into.accept(specs.get(i), built.get(i));
        }
    }

    Requirement requirement(ImportSpec i) {
        return imports.get(i);
    }

    Requirement requirement(RequireSpec q) {
        return requires.get(q);
    }

    /** Whether the requirement is effective at resolve time, others are not part of the model. */
    boolean effective(ImportSpec i) {
        return context.isEffective(imports.get(i));
    }
}
