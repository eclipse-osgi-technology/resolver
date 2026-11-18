/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.candidates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * What a resource sees of the packages on the current selection of candidates:
 * the packages it imports and exports, those it gets through required bundles and the combined sources of a package.
 * Views are created by a {@link PackageViewCache} and are only valid until it is invalidated.
 */
public final class PackageView {

    private final ResolverResource resource;
    private final PackageViewCache cache;
    private final Map<String, ResolverCapability> visible;
    private volatile Map<String, List<ResolverCapability>> required;

    PackageView(ResolverResource resource, PackageViewCache cache) {
        this.resource = resource;
        this.cache = cache;
        this.visible = Collections.unmodifiableMap(computeVisible());
    }

    /**
     * The capability of a package the resource imports (the selected candidate) or exports unless it imports
     * the package as well (the export is then substituted).
     *
     * @param name the name of the package
     * @return the capability or {@code null}
     */
    public ResolverCapability get(String name) {
        return visible.get(name);
    }

    /**
     * The capabilities of all packages the resource imports or exports, see {@link #get(String)}.
     *
     * @return the capabilities, must not be changed
     */
    public Collection<ResolverCapability> getCapabilities() {
        return visible.values();
    }

    /**
     * The packages the resource sees through its selected required bundles and what these reexport, by name.
     * A package the provider imports as well comes from where the provider gets it, not from its substituted export.
     *
     * @return the capabilities by package name, must not be changed
     */
    public Map<String, List<ResolverCapability>> getRequired() {
        Map<String, List<ResolverCapability>> result = required;
        if (result == null) {
            Map<String, List<ResolverCapability>> computed = new HashMap<>();
            Map<ResolverResource, Boolean> visited = new IdentityHashMap<>();
            visited.put(resource, Boolean.TRUE);
            for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
                addRequired(requirement, computed, visited);
            }
            result = Collections.unmodifiableMap(computed);
            required = result;
        }
        return result;
    }

    /**
     * The capabilities the resource sees a package from: what it imports, else its own export together with what the
     * required bundles provide (a package can be split over several bundles).
     *
     * @param name the name of the package
     * @return the capabilities, empty if the package is not seen
     */
    public List<ResolverCapability> getSources(String name) {
        ResolverCapability own = visible.get(name);
        List<ResolverCapability> result = new ArrayList<>();
        List<ResolverCapability> provided;
        if (own != null && own.getOwner() != resource) {
            provided = Collections.singletonList(own);
        } else {
            if (own != null) {
                result.add(own);
            }
            List<ResolverCapability> fromRequired = getRequired().get(name);
            provided = fromRequired == null ? Collections.<ResolverCapability> emptyList() : fromRequired;
        }
        // a provided package is split with what its provider gets from its own required bundles
        for (ResolverCapability capability : provided) {
            if (!result.contains(capability)) {
                result.add(capability);
            }
            ResolverResource provider = capability.getOwner();
            if (provider == resource) {
                continue;
            }
            PackageView providerView = cache.getView(provider);
            ResolverCapability providerExport = providerView.get(name);
            if (providerExport == null || providerExport.getOwner() != provider) {
                continue;
            }
            if (!result.contains(providerExport)) {
                result.add(providerExport);
            }
            List<ResolverCapability> split = providerView.getRequired().get(name);
            if (split != null) {
                for (ResolverCapability other : split) {
                    if (!result.contains(other)) {
                        result.add(other);
                    }
                }
            }
        }
        return result;
    }

    private Map<String, ResolverCapability> computeVisible() {
        Map<String, ResolverCapability> view = new HashMap<>(resource.exportedPackages());
        for (ResolverRequirement requirement : resource.getClassSpaceRequirements()) {
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                continue;
            }
            for (Candidate candidate : requirement.getCandidates()) {
                if (candidate.getState() == CandidateState.SELECTED) {
                    ResolverCapability provided = candidate.getCapability();
                    view.put(String.valueOf(provided.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)), provided);
                }
            }
        }
        return view;
    }

    private void addRequired(ResolverRequirement requirement, Map<String, List<ResolverCapability>> result,
            Map<ResolverResource, Boolean> visited) {
        if (!BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())) {
            return;
        }
        for (Candidate candidate : requirement.getCandidates()) {
            ResolverResource provider = candidate.getProvider();
            if (candidate.getState() != CandidateState.SELECTED || visited.put(provider, Boolean.TRUE) != null) {
                continue;
            }
            PackageView view = cache.getView(provider);
            List<ResolverCapability> exported = new ArrayList<>(provider.capabilitiesOf(PackageNamespace.PACKAGE_NAMESPACE));
            for (ResolverResource fragment : provider.getAttachedFragments()) {
                exported.addAll(fragment.capabilitiesOf(PackageNamespace.PACKAGE_NAMESPACE));
            }
            for (ResolverCapability capability : exported) {
                String name = String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
                List<ResolverCapability> sources = result.computeIfAbsent(name, k -> new ArrayList<>(1));
                ResolverCapability source = view.get(name);
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }
            for (ResolverRequirement next : provider.getClassSpaceRequirements()) {
                if (BundleNamespace.VISIBILITY_REEXPORT
                        .equals(next.getDirectives().get(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE))) {
                    addRequired(next, result, visited);
                }
            }
        }
    }
}
