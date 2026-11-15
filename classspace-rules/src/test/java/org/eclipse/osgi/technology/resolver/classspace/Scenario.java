/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.classspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.index.CandidateSource;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;

/**
 * Bundles described by name, a candidate graph over them in which every
 * matching capability is offered for every requirement, and the choice of the
 * provider for an import.
 * Bundles are described first, the graph is created when the first of them is
 * asked for.
 */
final class Scenario {

    private final Map<String, ResourceBuilder> builders = new LinkedHashMap<>();
    private final Map<String, ResolverResource> resources = new LinkedHashMap<>();

    /** Starts the description of a bundle. */
    ResourceBuilder bundle(String name) {
        if (builders.containsKey(name)) {
            return builders.get(name);
        }
        ResourceBuilder builder = new ResourceBuilder();
        builder.addCapability(new CapReqBuilder("osgi.identity").addAttribute("osgi.identity", name));
        builders.put(name, builder);
        return builder;
    }

    /** Attributes of an exported package that uses other packages. */
    static Attrs uses(String... packages) {
        return Attrs.create("uses:", String.join(",", packages));
    }

    ResolverResource resource(String name) {
        build();
        return resources.get(name);
    }

    private void build() {
        if (resources.isEmpty()) {
            builders.forEach((n, builder) -> resources.put(n, new ResolverResource(builder.build())));
            List<ResolverCapability> all = new ArrayList<>();
            resources.values().forEach(r -> all.addAll(r.getResolverCapabilities()));
            for (ResolverResource resource : resources.values()) {
                for (ResolverRequirement requirement : resource.getResolverRequirements()) {
                    for (ResolverCapability capability : all) {
                        if (ResourceUtils.matches(requirement.unwrap(), capability.unwrap())) {
                            requirement.addCandidate(capability);
                        }
                    }
                }
            }
        }
    }

    /** Selects the provider for the import of a package, every other candidate is deferred. */
    void select(String importer, String packageName, String provider) {
        ResolverRequirement requirement = importOf(importer, packageName);
        for (Candidate candidate : requirement.getCandidates()) {
            if (candidate.getProvider() == resource(provider)) {
                candidate.select("selected by the test");
            } else {
                candidate.defer("not selected by the test");
            }
        }
    }

    ResolverRequirement importOf(String importer, String packageName) {
        for (ResolverRequirement requirement : resource(importer).getResolverRequirements()) {
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())
                    && requirement.getDirectives().get("filter").contains("(" + PackageNamespace.PACKAGE_NAMESPACE + "="
                            + packageName + ")")) {
                return requirement;
            }
        }
        throw new IllegalArgumentException(importer + " does not import " + packageName);
    }

    /** What a resolver may choose from, the capabilities offered in the graph. */
    CandidateSource candidates() {
        build();
        return new CandidateSource() {
            @Override
            public List<? extends Capability> getCandidates(Requirement requirement) {
                if (requirement instanceof ResolverRequirement) {
                    List<Capability> result = new ArrayList<>();
                    for (Candidate candidate : ((ResolverRequirement) requirement).getCandidates()) {
                        result.add(candidate.getCapability());
                    }
                    return result;
                }
                return Collections.emptyList();
            }
        };
    }

    UsesRule usesRule() {
        return new UsesRule(new UsesIndex(resources(), candidates(), r -> true));
    }

    SubstitutionRule substitutionRule() {
        return new SubstitutionRule(new SubstitutionIndex(resources(), candidates(), r -> true, resources()));
    }

    ClassSpaceRules classSpaceRules() {
        return new ClassSpaceRules(resources(), Collections.emptyList());
    }

    private List<ResolverResource> resources() {
        build();
        return new ArrayList<>(resources.values());
    }
}
