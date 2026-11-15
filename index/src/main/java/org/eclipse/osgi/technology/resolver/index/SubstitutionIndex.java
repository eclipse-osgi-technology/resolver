/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The static part of package substitution, built once per resolve from
 * everything a resolver can choose from. Substitution decides which exports
 * exist at all, so a resolver can use it before it selects anything. <ul>
 * <li>the <em>substitutable</em> exports: exports of a package that the
 * exporting resource imports as well, with the import that decides about
 * them,</li> <li>the <em>demand</em> of each of them: the requirements of other
 * resources the export can satisfy, that is who loses its provider if the
 * export is discarded,</li> <li>the <em>pinned</em> exports: a resource that is
 * certain to be resolved needs the export because it has no other candidate, so
 * an import that would discard the export can not be allowed to (only if the
 * export matches its own import, else it can not be kept anyway).</li> </ul>
 * Only depends on the standard resource API, resources, requirements and
 * capabilities are keyed by identity.
 */
public final class SubstitutionIndex {

    private static final Pattern PACKAGE_FILTER = Pattern.compile("\\(" + Pattern.quote(PackageNamespace.PACKAGE_NAMESPACE) + "=([^)]*)\\)");

    private final Map<Capability, Substitution> byExport = new IdentityHashMap<>();
    private final Map<Requirement, Substitution> byImport = new IdentityHashMap<>();
    private final List<Substitution> substitutions = new ArrayList<>();

    /**
     * Indexes the resources reachable from the roots.
     *
     * @param roots the resources to start from, everything reachable through
     *     the candidates is indexed
     * @param candidates what a resolver may choose from for a requirement
     * @param expand tells if the requirements of a resource are followed, an
     *     already resolved resource only contributes its capabilities
     * @param certain the resources that are certain to be resolved if the
     *     resolve succeeds, only their requirements can pin an export
     */
    public SubstitutionIndex(Collection<? extends Resource> roots, CandidateSource candidates, Predicate<Resource> expand,
            Collection<? extends Resource> certain) {
        Set<Resource> certainSet = Collections.newSetFromMap(new IdentityHashMap<Resource, Boolean>());
        certainSet.addAll(certain);
        Set<Resource> seen = Collections.newSetFromMap(new IdentityHashMap<Resource, Boolean>());
        Map<Capability, List<Requirement>> demand = new IdentityHashMap<>();
        Map<Resource, Map<String, Requirement>> imports = new IdentityHashMap<>();
        Map<Requirement, List<? extends Capability>> offered = new IdentityHashMap<>();
        List<Resource> indexed = new ArrayList<>();
        Deque<Resource> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Resource resource = todo.pop();
            if (!seen.add(resource)) {
                continue;
            }
            indexed.add(resource);
            if (!expand.test(resource)) {
                continue;
            }
            Map<String, Requirement> byName = new HashMap<>();
            for (Requirement requirement : resource.getRequirements(null)) {
                if (PackageNamespace.RESOLUTION_DYNAMIC.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE))) {
                    continue;
                }
                List<? extends Capability> caps = candidates.getCandidates(requirement);
                if (caps == null) {
                    continue;
                }
                offered.put(requirement, caps);
                for (Capability capability : caps) {
                    todo.push(capability.getResource());
                    if (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
                        List<Requirement> list = demand.get(capability);
                        if (list == null) {
                            list = new ArrayList<>();
                            demand.put(capability, list);
                        }
                        list.add(requirement);
                    }
                }
                if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())
                        && !Namespace.CARDINALITY_MULTIPLE.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE))) {
                    String name = packageName(requirement, caps);
                    if (name != null && !byName.containsKey(name)) {
                        byName.put(name, requirement);
                    }
                }
            }
            imports.put(resource, byName);
        }
        for (Resource resource : indexed) {
            Map<String, Requirement> byName = imports.get(resource);
            if (byName == null || byName.isEmpty()) {
                continue;
            }
            for (Capability export : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                Requirement overlapping = byName.get(String.valueOf(export.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)));
                if (overlapping != null) {
                    add(export, overlapping, demand, offered, certainSet);
                }
            }
        }
    }

    private void add(Capability export, Requirement overlapping, Map<Capability, List<Requirement>> demand,
            Map<Requirement, List<? extends Capability>> offered, Set<Resource> certain) {
        List<Requirement> demanded = new ArrayList<>();
        Requirement pinnedBy = null;
        boolean keepable = false;
        for (Capability candidate : offered.get(overlapping)) {
            keepable |= candidate == export;
        }
        List<Requirement> all = demand.get(export);
        if (all != null) {
            for (Requirement requirement : all) {
                if (requirement == overlapping) {
                    continue;
                }
                demanded.add(requirement);
                if (keepable && pinnedBy == null && certain.contains(requirement.getResource())
                        && !Namespace.RESOLUTION_OPTIONAL.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE))
                        && !Namespace.CARDINALITY_MULTIPLE.equals(requirement.getDirectives().get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE))
                        && offered.get(requirement).size() == 1) {
                    pinnedBy = requirement;
                }
            }
        }
        Substitution substitution = new Substitution(export, overlapping, demanded, pinnedBy, keepable);
        byExport.put(export, substitution);
        byImport.put(overlapping, substitution);
        substitutions.add(substitution);
    }

    private static String packageName(Requirement requirement, List<? extends Capability> caps) {
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        if (filter != null) {
            Matcher matcher = PACKAGE_FILTER.matcher(filter);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return caps.isEmpty() ? null : String.valueOf(caps.get(0).getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
    }

    /**
     * All substitutable exports of the resolve.
     *
     * @return the substitutions in the order they were found
     */
    public List<Substitution> getSubstitutions() {
        return Collections.unmodifiableList(substitutions);
    }

    /**
     * Looks up the substitution of an export.
     *
     * @param export the exported capability
     * @return the substitution of the export or {@code null} if the export is
     * not substitutable
     */
    public Substitution getSubstitution(Capability export) {
        return byExport.get(export);
    }

    /**
     * Looks up the substitution that an import decides.
     *
     * @param overlappingImport the import of a package that its resource
     *     exports as well
     * @return the substitution that the import decides or {@code null} if it is
     * not an overlapping import
     */
    public Substitution getSubstitutionOfImport(Requirement overlappingImport) {
        return byImport.get(overlappingImport);
    }
}
