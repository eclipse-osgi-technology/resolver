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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.HostedCapability;

/**
 * A {@link Resource} taking part in a resolve operation.
 */
public class ResolverResource implements Resource {

    private final Resource wrapped;
    private final boolean fragment;
    private final List<ResolverCapability> capabilities = new ArrayList<>();
    private final List<ResolverRequirement> requirements = new ArrayList<>();
    private final Map<String, List<ResolverCapability>> capabilitiesByNamespace = new HashMap<>();
    private final Map<String, List<ResolverRequirement>> requirementsByNamespace = new HashMap<>();
    private final String identity;
    private final Version version;
    private final String name;
    private final Map<Capability, ResolverCapability> capabilityLookup = new HashMap<>();
    private final Map<Requirement, ResolverRequirement> requirementLookup = new HashMap<>();
    private ResolveState state;
    private ResolverRequirement failedRequirement;
    private UsesViolation usesViolation;
    private List<ResolverResource> attached = Collections.emptyList();
    private Map<String, ResolverCapability> exportedPackages;
    private final Set<ResolverResource> detachedHosts = Collections
            .newSetFromMap(new IdentityHashMap<>());
    private final Map<ResolverCapability, ResolverCapability> substituted = new LinkedHashMap<>();

    /**
     * Wraps a resource of a resolve operation.
     *
     * @param wrapped the resource to wrap
     */
    public ResolverResource(Resource wrapped) {
        this.wrapped = wrapped;
        this.fragment = !wrapped.getRequirements(HostNamespace.HOST_NAMESPACE).isEmpty();
        this.state = ResolveState.PENDING;
        for (Capability capability : wrapped.getCapabilities(null)) {
            ResolverCapability wrapper = capability(capability);
            capabilities.add(wrapper);
            capabilitiesByNamespace.computeIfAbsent(wrapper.getNamespace(), k -> new ArrayList<>()).add(wrapper);
        }
        for (Requirement requirement : wrapped.getRequirements(null)) {
            ResolverRequirement wrapper = new ResolverRequirement(this, requirement);
            requirements.add(wrapper);
            requirementsByNamespace.computeIfAbsent(wrapper.getNamespace(), k -> new ArrayList<>()).add(wrapper);
            requirementLookup.put(requirement, wrapper);
        }
        List<Capability> identities = wrapped.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
        if (identities.isEmpty()) {
            this.identity = null;
            this.version = Version.emptyVersion;
            this.name = wrapped.toString();
        } else {
            Map<String, Object> attributes = identities.get(0).getAttributes();
            Object identityValue = attributes.get(IdentityNamespace.IDENTITY_NAMESPACE);
            Object versionValue = attributes.get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
            this.identity = String.valueOf(identityValue);
            this.version = versionValue instanceof Version ? (Version) versionValue
                    : versionValue instanceof String ? Version.parseVersion((String) versionValue) : Version.emptyVersion;
            this.name = identityValue + "/" + versionValue;
        }
    }

    /**
     * The wrapped resource.
     *
     * @return the wrapped resource
     */
    public Resource unwrap() {
        return wrapped;
    }

    /**
     * A fragment requires a host, its capabilities and requirements are
     * provided and wired through that host.
     *
     * @return true if the resource is a fragment
     */
    public boolean isFragment() {
        return fragment;
    }

    /**
     * The hosts a fragment is attached to: the providers of the selected
     * candidates of its host requirement.
     *
     * @return the hosts, empty if there are none
     */
    public List<ResolverResource> getHosts() {
        List<ResolverResource> hosts = new ArrayList<>();
        for (ResolverRequirement requirement : requirementsOf(HostNamespace.HOST_NAMESPACE)) {
            for (Candidate candidate : requirement.getCandidates()) {
                if (candidate.getState() == CandidateState.SELECTED) {
                    hosts.add(candidate.getProvider());
                }
            }
        }
        return hosts;
    }

    /**
     * A fragment is detached from a host if it is not consistent with it: it is
     * not attached to that host any more, what the host provided through the
     * fragment is lost, while other hosts keep the fragment.
     *
     * @param host the host
     * @param reason why the fragment is detached
     */
    public void detachFrom(ResolverResource host, String reason) {
        detachedHosts.add(host);
        for (ResolverRequirement requirement : requirementsOf(HostNamespace.HOST_NAMESPACE)) {
            for (Candidate candidate : requirement.getCandidates()) {
                if (candidate.getProvider() == host && candidate.getState() == CandidateState.SELECTED) {
                    candidate.reject(reason);
                }
            }
        }
    }

    /**
     * Tells if this fragment is not attached to the host any more, see
     * {@link #detachFrom}.
     *
     * @param host the host
     * @return true if the fragment is detached from the host
     */
    public boolean isDetachedFrom(ResolverResource host) {
        return detachedHosts.contains(host);
    }

    /**
     * Records what became of an export of a package that this resource imports
     * as well.
     *
     * @param export an export of this resource
     * @param substitute the capability the package is imported from instead
     *     (the export is discarded) or {@code null} if the export is kept
     */
    public void decide(ResolverCapability export, ResolverCapability substitute) {
        substituted.put(export, substitute);
    }

    /**
     * True if the export is one that this resource imports as well, see
     * {@link #getSubstitute(ResolverCapability)}.
     *
     * @param export an export of this resource
     * @return true if the export is also imported
     */
    public boolean isSubstitutable(ResolverCapability export) {
        return substituted.containsKey(export);
    }

    /**
     * The capability the package is imported from instead of the export,
     * {@code null} if the export is kept.
     *
     * @param export an export of this resource
     * @return the substitute or {@code null} if the export is kept
     */
    public ResolverCapability getSubstitute(ResolverCapability export) {
        return substituted.get(export);
    }

    /**
     * Sets the fragments attached to this host.
     *
     * @param fragments the fragments to attach
     */
    public void setAttachedFragments(List<ResolverResource> fragments) {
        this.attached = new ArrayList<>(fragments);
        this.exportedPackages = null;
    }

    /**
     * The fragments attached to this host.
     *
     * @return the attached fragments
     */
    public List<ResolverResource> getAttachedFragments() {
        return attached;
    }

    /**
     * The requirements that shape the class space of this resource: its own and
     * those of the attached fragments that are still resolved (except the host
     * requirement), as a fragment contributes its imports to the host.
     *
     * @return the requirements
     */
    public List<ResolverRequirement> getClassSpaceRequirements() {
        List<ResolverRequirement> result = requirements;
        if (!attached.isEmpty()) {
            result = new ArrayList<>(requirements);
            for (ResolverResource fragment : attached) {
                if (fragment.getState() == ResolveState.RESOLVED) {
                    for (ResolverRequirement requirement : fragment.requirements) {
                        if (!HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                            result.add(requirement);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Wraps a capability of the fragment {@code declaring} as it is provided by
     * this host.
     *
     * @param hosted the hosted capability
     * @param declaring the fragment that declares the capability
     * @return the wrapped capability
     */
    public ResolverCapability hostedCapability(HostedCapability hosted, ResolverResource declaring) {
        return new ResolverCapability(this, hosted, declaring);
    }

    /**
     * The name from the identity capability, {@code null} if there is none.
     *
     * @return the identity or {@code null}
     */
    public String getIdentity() {
        return identity;
    }

    /**
     * The version from the identity capability, {@link Version#emptyVersion} if
     * there is none.
     *
     * @return the version
     */
    public Version getVersion() {
        return version;
    }

    /**
     * A readable name, the identity of the resource if it has one.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * The wrapper of a capability (or the wrapper itself), capabilities the
     * resource does not list itself (e.g. hosted ones) are wrapped on demand.
     * Only to be used while the graph is built.
     *
     * @param capability a capability of this resource or its wrapper
     * @return the wrapper
     */
    public ResolverCapability capability(Capability capability) {
        if (capability instanceof ResolverCapability) {
            return (ResolverCapability) capability;
        }
        ResolverCapability existingWrapper = capabilityLookup.get(capability);
        if (existingWrapper == null) {
            existingWrapper = new ResolverCapability(this, capability);
            capabilityLookup.put(capability, existingWrapper);
        }
        return existingWrapper;
    }

    /**
     * The wrapper of a requirement (or the wrapper itself) or {@code null} if
     * it is not a requirement of this resource.
     *
     * @param requirement a requirement of this resource or its wrapper
     * @return the wrapper or {@code null}
     */
    public ResolverRequirement requirement(Requirement requirement) {
        if (requirement instanceof ResolverRequirement) {
            return (ResolverRequirement) requirement;
        }
        return requirementLookup.get(requirement);
    }

    /**
     * The capabilities of this resource as wrappers.
     *
     * @return the capabilities, not modifiable
     */
    public List<ResolverCapability> getResolverCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    /**
     * The requirements of this resource as wrappers.
     *
     * @return the requirements, not modifiable
     */
    public List<ResolverRequirement> getResolverRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    /**
     * Where the resource is in the resolution.
     *
     * @return the state
     */
    public ResolveState getState() {
        return state;
    }

    /**
     * The requirement that prevents the resolution, only set for
     * {@link ResolveState#FAILED}.
     *
     * @return the requirement or {@code null}
     */
    public ResolverRequirement getFailedRequirement() {
        return failedRequirement;
    }

    /**
     * Marks the resource as resolvable.
     */
    public void markResolved() {
        this.state = ResolveState.RESOLVED;
    }

    /**
     * Marks the resource as not resolvable because of a requirement that has no
     * usable candidate.
     *
     * @param requirement the requirement that has no usable candidate
     */
    public void markFailed(ResolverRequirement requirement) {
        this.failedRequirement = requirement;
        this.state = ResolveState.FAILED;
    }

    /**
     * Marks the resource as not resolvable because of a class space
     * inconsistency.
     *
     * @param violation the violation that prevents the resolution
     */
    public void markFailed(UsesViolation violation) {
        if (violation.getHiddenImport() != null) {
            // the resource lacks a provider, there is nothing inconsistent
            // about its class space
            this.failedRequirement = violation.getHiddenImport();
        }
        this.usesViolation = violation;
        this.state = ResolveState.FAILED;
    }

    /**
     * The inconsistency that prevents the resolution, only set if it is the
     * reason for {@link ResolveState#FAILED}.
     *
     * @return the violation or {@code null}
     */
    public UsesViolation getUsesViolation() {
        return usesViolation;
    }

    /**
     * Every capability that shapes the class space of this resource: those of
     * its package view, its own capabilities of other namespaces and the
     * selected providers of its requirements in other namespaces.
     *
     * @return the capabilities
     */
    public List<ResolverCapability> getSeenCapabilities() {
        List<ResolverCapability> seen = new ArrayList<>(getPackageView().values());
        for (List<ResolverCapability> required : getRequiredPackages().values()) {
            seen.addAll(required);
        }
        for (ResolverCapability capability : capabilities) {
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
                seen.add(capability);
            }
        }
        for (ResolverRequirement requirement : getClassSpaceRequirements()) {
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                for (Candidate candidate : requirement.getCandidates()) {
                    if (candidate.getState() == CandidateState.SELECTED) {
                        seen.add(candidate.getCapability());
                    }
                }
            }
        }
        return seen;
    }

    /**
     * A package imported more than once (the fragments add imports to the host)
     * from different providers.
     *
     * @return the selected capabilities of the first such package or an empty
     * list
     */
    public List<ResolverCapability> getConflictingImports() {
        Map<String, ResolverCapability> imported = new HashMap<>();
        for (ResolverRequirement requirement : getClassSpaceRequirements()) {
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                for (Candidate candidate : requirement.getCandidates()) {
                    if (candidate.getState() == CandidateState.SELECTED) {
                        ResolverCapability provided = candidate.getCapability();
                        ResolverCapability other = imported.put(
                                String.valueOf(provided.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)), provided);
                        if (other != null && other.getDeclared() != provided.getDeclared()) {
                            return new ArrayList<>(Arrays.asList(other, provided));
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * The packages this resource exports by name, including those of its attached fragments. They are
     * fixed until the attached fragments change.
     */
    private Map<String, ResolverCapability> exportedPackages() {
        if (exportedPackages == null) {
            Map<String, ResolverCapability> exported = new HashMap<>();
            for (ResolverCapability capability : capabilitiesOf(PackageNamespace.PACKAGE_NAMESPACE)) {
                exported.put(String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)), capability);
            }
            // the packages a fragment exports are exported by the host
            for (ResolverResource fragment : attached) {
                for (ResolverCapability capability : fragment.capabilitiesOf(PackageNamespace.PACKAGE_NAMESPACE)) {
                    exported.putIfAbsent(String.valueOf(capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)),
                            capability);
                }
            }
            exportedPackages = exported;
        }
        return exportedPackages;
    }

    /**
     * The capabilities of the package namespace this resource sees: what it imports (the selected candidates)
     * and what it exports unless it imports the package as well (the export is then substituted).
     * The result is a copy that can be changed by the caller.
     */
    private Map<String, ResolverCapability> getPackageView() {
        Map<String, ResolverCapability> view = new HashMap<>(exportedPackages());
        for (ResolverRequirement requirement : getClassSpaceRequirements()) {
            if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                for (Candidate candidate : requirement.getCandidates()) {
                    if (candidate.getState() == CandidateState.SELECTED) {
                        ResolverCapability provided = candidate.getCapability();
                        view.put(String.valueOf(provided.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)), provided);
                    }
                }
            }
        }
        return view;
    }

    /**
     * The packages this resource sees through its selected required bundles and
     * what these reexport, by name. A package the provider imports as well
     * comes from where the provider gets it, not from its substituted export.
     * Imports of this resource are searched first, so a package it imports is
     * not seen through a required bundle, callers decide about that.
     *
     * @return the capabilities by package name
     */
    public Map<String, List<ResolverCapability>> getRequiredPackages() {
        Map<String, List<ResolverCapability>> result = new HashMap<>();
        Map<ResolverResource, Boolean> visited = new IdentityHashMap<>();
        visited.put(this, Boolean.TRUE);
        for (ResolverRequirement requirement : getClassSpaceRequirements()) {
            addRequired(requirement, result, visited);
        }
        return result;
    }

    private static void addRequired(ResolverRequirement requirement, Map<String, List<ResolverCapability>> result,
            Map<ResolverResource, Boolean> visited) {
        if (!BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())) {
            return;
        }
        for (Candidate candidate : requirement.getCandidates()) {
            ResolverResource provider = candidate.getProvider();
            if (candidate.getState() != CandidateState.SELECTED || visited.put(provider, Boolean.TRUE) != null) {
                continue;
            }
            Map<String, ResolverCapability> view = provider.getPackageView();
            List<ResolverCapability> exported = new ArrayList<>(provider.capabilitiesOf(PackageNamespace.PACKAGE_NAMESPACE));
            for (ResolverResource fragment : provider.attached) {
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

    /**
     * The capabilities this resource sees a package from: what it imports, else
     * its own export together with what the required bundles provide (a package
     * can be split over several bundles).
     *
     * @param name the name of the package
     * @return the capabilities, empty if the package is not seen
     */
    public List<ResolverCapability> getPackageSources(String name) {
        ResolverCapability own = getPackageView().get(name);
        List<ResolverCapability> result = new ArrayList<>();
        List<ResolverCapability> provided;
        if (own != null && own.getOwner() != this) {
            provided = Collections.singletonList(own);
        } else {
            if (own != null) {
                result.add(own);
            }
            List<ResolverCapability> required = getRequiredPackages().get(name);
            provided = required == null ? Collections.<ResolverCapability> emptyList() : required;
        }
        // a provided package is split with what its provider gets from its own
        // required bundles
        for (ResolverCapability capability : provided) {
            if (!result.contains(capability)) {
                result.add(capability);
            }
            ResolverResource provider = capability.getOwner();
            if (provider == this) {
                continue;
            }
            ResolverCapability providerExport = provider.getPackageView().get(name);
            if (providerExport == null || providerExport.getOwner() != provider) {
                continue;
            }
            if (!result.contains(providerExport)) {
                result.add(providerExport);
            }
            List<ResolverCapability> split = provider.getRequiredPackages().get(name);
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

    @Override
    public List<Capability> getCapabilities(String namespace) {
        return namespace == null ? new ArrayList<Capability>(capabilities)
                : new ArrayList<Capability>(capabilitiesOf(namespace));
    }

    @Override
    public List<Requirement> getRequirements(String namespace) {
        return namespace == null ? new ArrayList<Requirement>(requirements)
                : new ArrayList<Requirement>(requirementsOf(namespace));
    }

    private List<ResolverCapability> capabilitiesOf(String namespace) {
        return capabilitiesByNamespace.getOrDefault(namespace, Collections.emptyList());
    }

    private List<ResolverRequirement> requirementsOf(String namespace) {
        return requirementsByNamespace.getOrDefault(namespace, Collections.emptyList());
    }

    @Override
    public String toString() {
        return getName();
    }
}
