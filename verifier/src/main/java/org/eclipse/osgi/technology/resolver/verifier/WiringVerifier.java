/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

/**
 * Independent check that the result of a resolve operation is a well formed wiring of the resources that were asked
 * for (OSGi Core "Resolver Service Specification", section "Resolving" and "Fragments", and the namespace rules of
 * "Framework Module Layer"). It complements the {@link ConsistencyVerifier}, which assumes a well formed wiring and
 * only judges the class spaces. It never searches for a solution and never decides which candidate is the right one,
 * it only reports what a result must not look like:
 * <ul>
 * <li>a mandatory resource is not part of the result, or a resource nobody asked for (directly or through a wire)
 * is,</li>
 * <li>a mandatory and effective requirement of a resolved resource has no wire (an imported package the resource
 * also exports may stay unwired, it then uses its own export), this includes the requirements of
 * attached fragments for their host and the host requirement of the fragment itself,</li>
 * <li>a requirement with the cardinality {@code single} has more than one wire (a fragment may have several
 * hosts),</li>
 * <li>a wire that is not made by the resource it is listed for, belongs to a requirement the resource does not
 * have, is made for a requirement that is not effective, ends in a capability the provider does not offer, ends in a
 * capability that does not satisfy the requirement (namespace, {@code filter} directive, {@code mandatory}
 * directive) or ends in a resource that is not part of the result,</li>
 * <li>a fragment is attached to another fragment.</li>
 * </ul>
 * Singletons and the order of candidates are no concern of the resolver but of the resolve context and are therefore
 * not checked. Existing wirings are not supported, so every provider has to be part of the result.
 * <p>
 * Fragments are checked as common resolvers wire them: the payload requirements of a fragment (everything but
 * {@code osgi.wiring.host} and {@code osgi.ee}) are wired for the host, the wire keeps the requirement declared by
 * the fragment. The capabilities of a fragment are provided by the host, a hosted capability is recognized by its
 * namespace, attributes and directives.
 */
public final class WiringVerifier {

    private final Collection<? extends Resource> mandatory;
    private final Collection<? extends Resource> optional;
    private final Map<Resource, List<Wire>> result;

    /**
     * Creates an verifier for a result.
     *
     * @param mandatory the resources that had to be resolved
     * @param optional the resources that may be part of the result, but do not have to
     * @param result the wires of each resolved resource
     */
    public WiringVerifier(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional,
            Map<Resource, List<Wire>> result) {
        this.mandatory = mandatory;
        this.optional = optional;
        this.result = result;
    }

    /**
     * Checks that the result is a well formed wiring of the requested resources.
     *
     * @return a description of each problem, empty if the result is a well formed wiring
     */
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        checkRequestedResources(violations);
        for (Map.Entry<Resource, List<Wire>> entry : result.entrySet()) {
            Resource resource = entry.getKey();
            List<Wire> wires = entry.getValue() == null ? Collections.<Wire>emptyList() : entry.getValue();
            for (Wire wire : wires) {
                checkWire(resource, wire, violations);
            }
            checkRequirements(resource, wires, violations);
        }
        return violations;
    }

    private void checkRequestedResources(List<String> violations) {
        for (Resource resource : mandatory) {
            if (!result.containsKey(resource)) {
                violations.add("mandatory " + describe(resource) + " is not part of the result");
            }
        }
        Set<Resource> reachable = reachable();
        for (Resource resource : result.keySet()) {
            if (!reachable.contains(resource)) {
                violations.add(describe(resource) + " is part of the result but neither requested nor wired to by a"
                        + " requested resource");
            }
        }
    }

    /** What was asked for plus everything wired to, fragments of a reachable host may come in as related resources. */
    private Set<Resource> reachable() {
        Set<Resource> reachable = new HashSet<>();
        Deque<Resource> pending = new ArrayDeque<>();
        pending.addAll(mandatory);
        pending.addAll(optional);
        while (true) {
            while (!pending.isEmpty()) {
                Resource resource = pending.poll();
                if (result.containsKey(resource) && reachable.add(resource)) {
                    for (Wire wire : wires(resource)) {
                        pending.add(wire.getProvider());
                    }
                }
            }
            for (Resource resource : result.keySet()) {
                for (Wire wire : wires(resource)) {
                    if (isHostWire(wire) && reachable.contains(wire.getProvider())) {
                        pending.add(resource);
                    }
                }
            }
            pending.removeAll(reachable);
            if (pending.isEmpty()) {
                return reachable;
            }
        }
    }

    private void checkWire(Resource resource, Wire wire, List<String> violations) {
        if (wire == null) {
            violations.add(describe(resource) + " has a null wire");
            return;
        }
        Requirement requirement = wire.getRequirement();
        Capability capability = wire.getCapability();
        String text = describe(resource) + " wire " + describe(requirement) + " -> " + describe(capability);
        if (!resource.equals(wire.getRequirer())) {
            violations.add(text + " is listed for " + describe(resource) + " but its requirer is "
                    + describe(wire.getRequirer()));
        }
        if (!ownedRequirement(resource, requirement)) {
            violations.add(text + " uses a requirement that " + describe(resource)
                    + " neither declares nor gets from an attached fragment");
        }
        if (!isEffective(requirement)) {
            violations.add(text + " is made for a requirement that is not effective");
        }
        if (!requirement.getNamespace().equals(capability.getNamespace())) {
            violations.add(text + " connects different namespaces");
        } else if (!filterMatches(requirement, capability)) {
            violations.add(text + " ends in a capability that does not match the filter "
                    + requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE));
        } else if (!mandatoryAttributesAreRequested(requirement, capability)) {
            violations.add(text + " ends in a capability whose mandatory attributes are not requested");
        }
        Resource provider = wire.getProvider();
        if (!result.containsKey(provider)) {
            violations.add(text + " ends in " + describe(provider) + " which is not part of the result");
        } else if (!offers(provider, capability)) {
            violations.add(text + " ends in a capability that " + describe(provider) + " does not offer");
        }
        if (isHostWire(wire) && result.containsKey(provider) && isFragment(provider)) {
            violations.add(text + " attaches a fragment to the fragment " + describe(provider));
        }
    }

    private void checkRequirements(Resource resource, List<Wire> wires, List<String> violations) {
        boolean fragment = isFragment(resource);
        for (Requirement requirement : requirementsOf(resource)) {
            if (fragment && !requirement.getResource().equals(resource)) {
                continue;
            }
            List<Wire> own = new ArrayList<>();
            for (Wire wire : wires) {
                if (sameRequirement(wire.getRequirement(), requirement)) {
                    own.add(wire);
                }
            }
            boolean required = isEffective(requirement) && isMandatory(requirement)
                    && (!fragment || isNotPayload(requirement));
            if (required && own.isEmpty() && !satisfiedByOwnExport(resource, requirement)) {
                violations.add(describe(resource) + " is part of the result but its requirement "
                        + describe(requirement) + " has no wire");
            }
            if (own.size() > 1 && !isMultiple(requirement) && !HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                violations.add(describe(resource) + " has " + own.size() + " wires for the single cardinality"
                        + " requirement " + describe(requirement));
            }
            Set<Capability> distinct = new HashSet<>();
            for (Wire wire : own) {
                if (!distinct.add(wire.getCapability())) {
                    violations.add(describe(resource) + " has more than one wire from " + describe(requirement)
                            + " to " + describe(wire.getCapability()));
                }
            }
        }
    }

    /** An imported package that is also exported may stay unwired, the resource then uses its own export. */
    private boolean satisfiedByOwnExport(Resource resource, Requirement requirement) {
        if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
            for (Capability capability : resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (filterMatches(requirement, capability)
                        && mandatoryAttributesAreRequested(requirement, capability)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The own requirements of a resource and, for a host, the payload requirements of its attached fragments. */
    private List<Requirement> requirementsOf(Resource resource) {
        List<Requirement> requirements = new ArrayList<>(resource.getRequirements(null));
        if (!isFragment(resource)) {
            for (Resource fragment : fragmentsOf(resource)) {
                for (Requirement requirement : fragment.getRequirements(null)) {
                    if (!isNotPayload(requirement)) {
                        requirements.add(requirement);
                    }
                }
            }
        }
        return requirements;
    }

    private boolean ownedRequirement(Resource resource, Requirement requirement) {
        for (Requirement candidate : requirementsOf(resource)) {
            if (sameRequirement(candidate, requirement)) {
                return true;
            }
        }
        return false;
    }

    private boolean offers(Resource provider, Capability capability) {
        List<Resource> declaring = new ArrayList<>();
        declaring.add(provider);
        declaring.addAll(fragmentsOf(provider));
        for (Resource resource : declaring) {
            for (Capability candidate : resource.getCapabilities(capability.getNamespace())) {
                if (sameCapability(candidate, capability)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The fragments of the result that have a host wire to the resource. */
    private List<Resource> fragmentsOf(Resource host) {
        List<Resource> fragments = new ArrayList<>();
        for (Resource candidate : result.keySet()) {
            for (Wire wire : wires(candidate)) {
                if (isHostWire(wire) && host.equals(wire.getProvider())) {
                    fragments.add(candidate);
                    break;
                }
            }
        }
        return fragments;
    }

    private boolean isFragment(Resource resource) {
        return !resource.getRequirements(HostNamespace.HOST_NAMESPACE).isEmpty();
    }

    private boolean isHostWire(Wire wire) {
        return HostNamespace.HOST_NAMESPACE.equals(wire.getRequirement().getNamespace());
    }

    private static boolean isNotPayload(Requirement requirement) {
        return HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())
                || ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE.equals(requirement.getNamespace());
    }

    /** Same rule as the resolve context of the specification example: no directive or {@code resolve}. */
    private static boolean isEffective(Requirement requirement) {
        String effective = requirement.getDirectives().get(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE);
        return effective == null || Namespace.EFFECTIVE_RESOLVE.equals(effective);
    }

    /** Optional and dynamic (package namespace) requirements may stay unwired when the resource is resolved. */
    private static boolean isMandatory(Requirement requirement) {
        String resolution = requirement.getDirectives().get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE);
        return resolution == null || Namespace.RESOLUTION_MANDATORY.equals(resolution);
    }

    private static boolean isMultiple(Requirement requirement) {
        return Namespace.CARDINALITY_MULTIPLE
                .equals(requirement.getDirectives().get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE));
    }

    private static boolean filterMatches(Requirement requirement, Capability capability) {
        String text = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        if (text == null) {
            return true;
        }
        try {
            Filter filter = FrameworkUtil.createFilter(text);
            return filter.matches(capability.getAttributes());
        } catch (InvalidSyntaxException e) {
            return false;
        }
    }

    /** The {@code mandatory} directive of the osgi.wiring.* namespaces: every named attribute has to be requested. */
    private static boolean mandatoryAttributesAreRequested(Requirement requirement, Capability capability) {
        String mandatory = capability.getDirectives().get(PackageNamespace.CAPABILITY_MANDATORY_DIRECTIVE);
        if (mandatory == null || !capability.getNamespace().startsWith("osgi.wiring.")) {
            return true;
        }
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        for (String attribute : mandatory.split("\\s*,\\s*")) {
            if (filter == null || !Pattern.compile("\\(" + Pattern.quote(attribute.trim()) + "\\s*[=<>~]")
                    .matcher(filter).find()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRequirement(Requirement a, Requirement b) {
        return a == b || a.equals(b) || a.getNamespace().equals(b.getNamespace())
                && a.getAttributes().equals(b.getAttributes()) && a.getDirectives().equals(b.getDirectives())
                && a.getResource().equals(b.getResource());
    }

    /** Compared by content as a hosted capability is a copy of the declared one with another resource. */
    private static boolean sameCapability(Capability a, Capability b) {
        return a == b || a.equals(b) || a.getNamespace().equals(b.getNamespace())
                && a.getDirectives().equals(b.getDirectives()) && sameAttributes(a, b);
    }

    /** A hosted package capability carries the identity of the host, so these attributes are not compared. */
    private static boolean sameAttributes(Capability a, Capability b) {
        return withoutBundleIdentity(a).equals(withoutBundleIdentity(b));
    }

    private static Map<String, Object> withoutBundleIdentity(Capability capability) {
        Map<String, Object> attributes = new HashMap<>(capability.getAttributes());
        if (PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
            attributes.remove(PackageNamespace.CAPABILITY_BUNDLE_SYMBOLICNAME_ATTRIBUTE);
            attributes.remove(PackageNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE);
        }
        return attributes;
    }

    private List<Wire> wires(Resource resource) {
        List<Wire> wires = new ArrayList<>();
        List<Wire> declared = result.get(resource);
        if (declared != null) {
            for (Wire wire : declared) {
                if (wire != null) {
                    wires.add(wire);
                }
            }
        }
        return wires;
    }

    private static String describe(Resource resource) {
        if (resource == null) {
            return "null";
        }
        List<Capability> identity = resource.getCapabilities("osgi.identity");
        return identity.isEmpty() ? resource.toString()
                : String.valueOf(identity.get(0).getAttributes().get("osgi.identity"));
    }

    private static String describe(Requirement requirement) {
        return "(" + describe(requirement.getResource()) + " requires " + requirement.getNamespace() + " "
                + requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE) + ")";
    }

    private static String describe(Capability capability) {
        return "(" + describe(capability.getResource()) + " provides " + capability.getNamespace() + " "
                + capability.getAttributes() + ")";
    }
}
