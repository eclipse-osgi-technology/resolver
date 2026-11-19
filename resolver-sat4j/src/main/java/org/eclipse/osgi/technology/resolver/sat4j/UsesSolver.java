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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.eclipse.osgi.technology.resolver.conflicts.UsesGraph;
import org.eclipse.osgi.technology.resolver.conflicts.UsesSession;
import org.eclipse.osgi.technology.resolver.index.Substitution;
import org.eclipse.osgi.technology.resolver.index.SubstitutionIndex;
import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;

/**
 * Decides the open choices of a resolve with a pseudo boolean solver once the selection by priority has left a class
 * space inconsistent: instead of changing one choice at a time and checking what can see it, the whole remaining
 * problem is handed to the solver at once.
 * <p>
 * The problem is built from the graph as the rule based front end left it: only resources that passed the local and
 * cross resource checks take part, only candidates that are selected or still alternatives are offered, exports that
 * are pinned by substitution are already decided, and only package names that can reach a contested package get
 * variables (the same pruning the class space rule relies on). The encoded rule is the class space rule:
 * <ul>
 * <li>{@code R(r)}: the resource is resolved, {@code W(c)}: the candidate is wired (for a fragment's host candidate
 * this is the attachment to that host), {@code A(e)}: an export that its own bundle imports as well is kept.</li>
 * <li>{@code D(r,p,e)}: r sees package p directly from export e, by an import, its own export or a required bundle
 * (its package sources), {@code X(r,b)}: r sees the exports of bundle b through Require-Bundle, {@code I(r,p)}: r
 * imports p, so what required bundles provide for p is shadowed. These are defined exactly by the wires, as the
 * solver uses them in both directions.</li>
 * <li>{@code S(r,c)}: r sees capability c, directly or implied: whoever sees a capability sees the direct package
 * sources of its exporter for every package the capability uses. Only ever implied, as it is only used negatively.</li>
 * </ul>
 * For every capability a resource sees, its direct sources of a used package and the exporter's direct sources must
 * be compatible: one set of exporters contains the other, or the package is split over bundles the resource
 * requires. Two direct sources of one package are only legal as such a split as well.
 * The objective prefers, in this order: every optional resource resolved, no resource given up and no fragment
 * detached, every optional requirement wired, and the candidate the context ranked first.
 * A changed choice names the conflict it is related to through the uses graph, not a chain of reasoning.
 */
final class UsesSolver extends PseudoBooleanProblem {

    final List<ResolverResource> live = new ArrayList<>();
    private final Set<ResolverResource> mandatoryRoots = Collections.newSetFromMap(new IdentityHashMap<ResolverResource, Boolean>());
    private final Set<ResolverResource> optionalRoots = Collections.newSetFromMap(new IdentityHashMap<ResolverResource, Boolean>());
    private final UsesIndex index;
    final UsesGraph graph;
    private final SubstitutionIndex substitutions;
    private final long timeoutSeconds;

    final Map<ResolverResource, Integer> resolved = new IdentityHashMap<>();
    final Map<Candidate, Integer> wires = new IdentityHashMap<>();
    final Map<ResolverRequirement, List<Candidate>> usable = new IdentityHashMap<>();
    final Map<ResolverCapability, Integer> active = new IdentityHashMap<>();
    private final Map<ResolverResource, Map<ResolverResource, Integer>> attachments = new IdentityHashMap<>();
    private final Map<ResolverResource, Set<String>> exported = new IdentityHashMap<>();
    private final Map<ResolverResource, Map<String, Map<ResolverCapability, Integer>>> direct = new IdentityHashMap<>();
    private final Map<ResolverResource, Map<ResolverCapability, Integer>> sees = new IdentityHashMap<>();
    private final Map<ResolverResource, Map<String, Integer>> imported = new IdentityHashMap<>();
    private final Map<ResolverResource, Map<ResolverResource, Integer>> bundleViews = new IdentityHashMap<>();
    private final Map<ResolverResource, List<ResolverResource>> bundleUsers = new IdentityHashMap<>();
    /**
     * Per exporter and used package the viewers that see a capability of the exporter using the package.
     */
    private final Map<ResolverResource, Map<String, List<SeenUse>>> watchers = new IdentityHashMap<>();
    private final Terms importTerms = new Terms();
    private final Terms bundleTerms = new Terms();
    private final Terms directTerms = new Terms();
    private final Deque<Object[]> work = new ArrayDeque<>();
    private final OwnerCache ownerCache = new OwnerCache(this);

    UsesSolver(UsesSession session, long timeoutSeconds) {
        this.index = session.getUsesIndex();
        this.graph = session.getUsesGraph();
        this.substitutions = session.getSubstitutionIndex();
        this.timeoutSeconds = timeoutSeconds;
        mandatoryRoots.addAll(session.getMandatory());
        optionalRoots.addAll(session.getOptional());
        for (ResolverResource resource : session.getResources()) {
            if (resource.getState() == ResolveState.RESOLVED) {
                live.add(resource);
            }
        }
    }

    /**
     * Builds and solves the problem.
     *
     * @param violations the resources whose class space is inconsistent after the selection, with the first violation
     *                   found, they are what the solution is explained with
     * @return the solution or {@code null} if there is none (or none was found in time)
     */
    Solution solve(Map<ResolverResource, UsesViolation> violations) {
        encode();
        boolean[] truth = solve(timeoutSeconds);
        return truth == null ? null : new Solution(this, truth, violations);
    }

    private void encode() {
        for (ResolverResource resource : live) {
            resolved.put(resource, newVariable());
        }
        for (ResolverResource resource : live) {
            collectUsable(resource);
        }
        for (ResolverResource resource : live) {
            Set<String> names = new HashSet<>();
            for (ResolverCapability capability : resource.getResolverCapabilities()) {
                String name = packageName(capability);
                if (name != null) {
                    names.add(name);
                }
            }
            for (ResolverCapability capability : resource.getExportedPackages().values()) {
                names.add(packageName(capability));
            }
            exported.put(resource, names);
        }
        for (ResolverResource resource : live) {
            if (resource.isFragment()) {
                collectAttachments(resource);
            }
        }
        for (ResolverResource resource : live) {
            encodeResource(resource);
        }
        propagate();
        importTerms.close(this);
        bundleTerms.close(this);
        directTerms.close(this);
        conflicts();
    }

    /**
     * The candidates a resource may still be wired to: the selected one and the alternatives behind it.
     */
    private void collectUsable(ResolverResource resource) {
        for (ResolverRequirement requirement : resource.getResolverRequirements()) {
            if (!requirement.isEffective()) {
                continue;
            }
            List<Candidate> candidates = new ArrayList<>();
            for (Candidate candidate : requirement.getCandidates()) {
                if (candidate.getState() == CandidateState.SELECTED || candidate.isAlternative()) {
                    candidates.add(candidate);
                    wires.put(candidate, newVariable());
                }
            }
            usable.put(requirement, candidates);
        }
    }

    /**
     * The attachment of a fragment to a host is the wire of its host requirement to that host.
     */
    private void collectAttachments(ResolverResource fragment) {
        Map<ResolverResource, Integer> byHost = new IdentityHashMap<>();
        for (ResolverRequirement requirement : fragment.getResolverRequirements()) {
            if (HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace())) {
                for (Candidate candidate : usable.getOrDefault(requirement, Collections.<Candidate> emptyList())) {
                    if (candidate.getState() == CandidateState.SELECTED) {
                        byHost.put(candidate.getProvider(), wires.get(candidate));
                    }
                }
            }
        }
        attachments.put(fragment, byHost);
    }

    private void encodeResource(ResolverResource resource) {
        int self = resolved.get(resource);
        if (mandatoryRoots.contains(resource)) {
            clause(self);
        } else {
            int missed = newVariable();
            clause(self, missed);
            (optionalRoots.contains(resource) ? missedOptional : givenUp).add(missed);
        }
        for (ResolverRequirement requirement : resource.getResolverRequirements()) {
            if (usable.containsKey(requirement)) {
                encodeRequirement(resource, self, requirement);
            }
        }
        for (ResolverCapability capability : resource.getResolverCapabilities()) {
            String name = packageName(capability);
            if (name != null) {
                if (index.reachesContested(name)) {
                    int exportSeen = directVariable(resource, name, capability);
                    clause(not(activeLiteral(capability)), exportSeen);
                    directTerms.add(exportSeen, activeLiteral(capability));
                }
            } else if (index.reachesContested(capability.getUses())) {
                // a capability of another namespace with uses shapes the class space of its owner as well
                clause(-self, seesVariable(resource, capability));
            }
        }
        for (ResolverCapability capability : resource.getExportedPackages().values()) {
            String name = packageName(capability);
            if (capability.getDeclaringResource() != resource && index.reachesContested(name)) {
                // the packages of a fragment are provided by its host as long as it is attached
                int exportSeen = directVariable(resource, name, capability);
                int attachment = attachmentLiteral(capability);
                clause(not(activeLiteral(capability)), not(attachment), exportSeen);
                directTerms.add(exportSeen, activeLiteral(capability), attachment);
            }
        }
    }

    private void encodeRequirement(ResolverResource owner, int self, ResolverRequirement requirement) {
        List<Candidate> candidates = usable.get(requirement);
        int[] literals = new int[candidates.size()];
        boolean host = HostNamespace.HOST_NAMESPACE.equals(requirement.getNamespace());
        boolean free = !host && !requirement.isMultiple();
        for (int i = 0; i < literals.length; i++) {
            Candidate candidate = candidates.get(i);
            int wire = wires.get(candidate);
            literals[i] = wire;
            clause(-wire, self);
            ResolverResource provider = candidate.getProvider();
            clause(-wire, resolvedLiteral(provider));
            int attachment = attachmentLiteral(candidate.getCapability());
            clause(-wire, attachment);
            int activeLiteral = activeLiteral(candidate.getCapability());
            if (activeLiteral != resolvedLiteral(provider)) {
                clause(-wire, activeLiteral);
            }
            if (free && i > 0) {
                preference.merge(wire, (long) i, Long::sum);
            }
        }
        if (host) {
            encodeAttachments(owner, self, literals);
        } else if (requirement.isMultiple()) {
            for (int i = 0; i < literals.length; i++) {
                Candidate candidate = candidates.get(i);
                clause(-self, not(resolvedLiteral(candidate.getProvider())), not(attachmentLiteral(candidate.getCapability())),
                        literals[i]);
            }
        } else {
            if (literals.length > 1) {
                atMostOne.add(literals.clone());
            }
            if (!requirement.isOptional()) {
                clause(with(literals, -self));
            } else if (literals.length > 0) {
                int unwired = newVariable();
                clause(with(literals, -self, unwired));
                preference.put(unwired, (long) literals.length + 1);
            }
        }
        String namespace = requirement.getNamespace();
        Map<ResolverResource, Integer> hosts = owner.isFragment() ? attachments.get(owner) : null;
        if (PackageNamespace.PACKAGE_NAMESPACE.equals(namespace)) {
            for (int i = 0; i < literals.length; i++) {
                ResolverCapability capability = candidates.get(i).getCapability();
                String name = packageName(capability);
                if (name == null || !index.reachesContested(name)) {
                    continue;
                }
                int seen = directVariable(owner, name, capability);
                clause(-literals[i], seen);
                directTerms.add(seen, literals[i]);
                int importedByOwner = importedVariable(owner, name);
                clause(-literals[i], importedByOwner);
                importTerms.add(importedByOwner, literals[i]);
                if (hosts != null) {
                    for (Map.Entry<ResolverResource, Integer> entry : hosts.entrySet()) {
                        int seenByHost = directVariable(entry.getKey(), name, capability);
                        clause(-literals[i], -entry.getValue(), seenByHost);
                        directTerms.add(seenByHost, literals[i], entry.getValue());
                        int importedByHost = importedVariable(entry.getKey(), name);
                        clause(-literals[i], -entry.getValue(), importedByHost);
                        importTerms.add(importedByHost, literals[i], entry.getValue());
                    }
                }
            }
        } else if (!host && !BundleNamespace.BUNDLE_NAMESPACE.equals(namespace)) {
            for (int i = 0; i < literals.length; i++) {
                ResolverCapability capability = candidates.get(i).getCapability();
                if (index.reachesContested(capability.getUses())) {
                    clause(-literals[i], seesVariable(owner, capability));
                    if (hosts != null) {
                        for (Map.Entry<ResolverResource, Integer> entry : hosts.entrySet()) {
                            clause(-literals[i], -entry.getValue(), seesVariable(entry.getKey(), capability));
                        }
                    }
                }
            }
        } else if (BundleNamespace.BUNDLE_NAMESPACE.equals(namespace)) {
            for (int i = 0; i < literals.length; i++) {
                ResolverResource provider = candidates.get(i).getProvider();
                int bundle = bundleVariable(owner, provider);
                clause(-literals[i], bundle);
                bundleTerms.add(bundle, literals[i]);
                if (hosts != null) {
                    for (Map.Entry<ResolverResource, Integer> entry : hosts.entrySet()) {
                        int hosted = bundleVariable(entry.getKey(), provider);
                        clause(-literals[i], -entry.getValue(), hosted);
                        bundleTerms.add(hosted, literals[i], entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * A fragment that was asked for explicitly is attached to every host it was selected for, any other fragment is
     * attached as long as that is consistent, else it is left out of that host at a cost.
     */
    private void encodeAttachments(ResolverResource fragment, int self, int[] literals) {
        if (mandatoryRoots.contains(fragment)) {
            for (int literal : literals) {
                clause(literal);
            }
            return;
        }
        clause(with(literals, -self));
        for (int literal : literals) {
            int detached = newVariable();
            clause(literal, detached);
            givenUp.add(detached);
        }
    }

    /**
     * A wire to a capability of a fragment needs the fragment attached to the providing host.
     */
    private int attachmentLiteral(ResolverCapability capability) {
        ResolverResource declaring = capability.getDeclaringResource();
        if (declaring == capability.getOwner()) {
            return TRUE;
        }
        Map<ResolverResource, Integer> hosts = attachments.get(declaring);
        Integer literal = hosts == null ? null : hosts.get(capability.getOwner());
        return literal == null ? FALSE : literal;
    }

    /**
     * An export is active if its resource is resolved; one that the resource imports as well only if that import is
     * not wired to another provider (substitution, Core specification 3.8.1).
     */
    private int activeLiteral(ResolverCapability capability) {
        Integer known = active.get(capability);
        if (known != null) {
            return known;
        }
        ResolverResource owner = capability.getOwner();
        int self = resolvedLiteral(owner);
        Substitution substitution = PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())
                ? substitutions.getSubstitution(capability) : null;
        if (substitution == null || !usable.containsKey(substitution.getOverlappingImport())) {
            active.put(capability, self);
            return self;
        }
        int export = newVariable();
        active.put(capability, export);
        List<Integer> others = new ArrayList<>();
        for (Candidate candidate : usable.get(substitution.getOverlappingImport())) {
            if (candidate.getCapability() != capability) {
                int wire = wires.get(candidate);
                clause(-export, -wire);
                others.add(wire);
            }
        }
        int[] kept = new int[others.size() + 2];
        kept[0] = not(self);
        kept[1] = export;
        for (int i = 0; i < others.size(); i++) {
            kept[i + 2] = others.get(i);
        }
        clause(kept);
        return export;
    }

    int resolvedLiteral(ResolverResource resource) {
        Integer variable = resolved.get(resource);
        if (variable != null) {
            return variable;
        }
        // not asked about: not usable
        return FALSE;
    }

    /**
     * The resource sees the package directly from the export: imported, exported by itself or provided by a required
     * bundle.
     */
    private int directVariable(ResolverResource resource, String name, ResolverCapability provider) {
        Map<ResolverCapability, Integer> providers = direct.computeIfAbsent(resource, k -> new LinkedHashMap<String, Map<ResolverCapability, Integer>>())
                .computeIfAbsent(name, k -> new IdentityHashMap<ResolverCapability, Integer>());
        Integer variable = providers.get(provider);
        if (variable == null) {
            variable = newVariable();
            providers.put(provider, variable);
            directTerms.define(variable);
            // a direct source is seen, so what it uses is followed
            clause(-variable, seesVariable(resource, provider));
            work.add(new Object[] { "D", resource, name, provider });
        }
        return variable;
    }

    /**
     * The resource sees the capability, directly or implied through the uses of something else it sees.
     */
    private int seesVariable(ResolverResource resource, ResolverCapability capability) {
        Map<ResolverCapability, Integer> byCapability = sees.computeIfAbsent(resource, k -> new IdentityHashMap<ResolverCapability, Integer>());
        Integer variable = byCapability.get(capability);
        if (variable == null) {
            variable = newVariable();
            byCapability.put(capability, variable);
            work.add(new Object[] { "S", resource, capability });
        }
        return variable;
    }

    /**
     * True if the resource (or a fragment attached to it) imports the package, the terms that make it true are recorded
     * with {@link #term}.
     */
    private int importedVariable(ResolverResource resource, String name) {
        Map<String, Integer> byName = imported.computeIfAbsent(resource, k -> new LinkedHashMap<String, Integer>());
        Integer variable = byName.get(name);
        if (variable == null) {
            variable = newVariable();
            byName.put(name, variable);
            importTerms.define(variable);
        }
        return variable;
    }

    private int bundleVariable(ResolverResource resource, ResolverResource bundle) {
        Map<ResolverResource, Integer> byBundle = bundleViews.computeIfAbsent(resource, k -> new IdentityHashMap<ResolverResource, Integer>());
        Integer variable = byBundle.get(bundle);
        if (variable == null) {
            variable = newVariable();
            byBundle.put(bundle, variable);
            bundleTerms.define(variable);
            bundleUsers.computeIfAbsent(bundle, k -> new ArrayList<ResolverResource>()).add(resource);
            work.add(new Object[] { "X", resource, bundle });
        }
        return variable;
    }

    /**
     * The implications between the views, added until no new view appears.
     */
    private void propagate() {
        while (!work.isEmpty()) {
            Object[] item = work.poll();
            String kind = (String) item[0];
            if ("S".equals(kind)) {
                propagateSeen((ResolverResource) item[1], (ResolverCapability) item[2]);
            } else if ("D".equals(kind)) {
                propagateDirect((ResolverResource) item[1], (String) item[2], (ResolverCapability) item[3]);
            } else {
                propagateBundle((ResolverResource) item[1], (ResolverResource) item[2]);
            }
        }
    }

    /**
     * A viewer that sees a capability sees the direct sources of its exporter for every package the capability uses
     * (and has to agree with them, see {@link #conflicts()}). What an already resolved exporter sees is not known.
     */
    private void propagateSeen(ResolverResource viewer, ResolverCapability capability) {
        ResolverResource exporter = capability.getOwner();
        if (exporter == viewer) {
            return;
        }
        int literal = sees.get(viewer).get(capability);
        for (String used : capability.getUses()) {
            if (!index.reachesContested(used)) {
                continue;
            }
            watchers.computeIfAbsent(exporter, k -> new LinkedHashMap<String, List<SeenUse>>())
                    .computeIfAbsent(used, k -> new ArrayList<SeenUse>()).add(new SeenUse(viewer, capability, literal));
            Map<ResolverCapability, Integer> sources = direct.getOrDefault(exporter, Collections.<String, Map<ResolverCapability, Integer>> emptyMap()).get(used);
            if (sources != null) {
                for (Map.Entry<ResolverCapability, Integer> source : new ArrayList<>(sources.entrySet())) {
                    seen(literal, source.getValue(), viewer, source.getKey());
                }
            }
        }
    }

    private void seen(int sees, int source, ResolverResource viewer, ResolverCapability capability) {
        clauseOnce(-sees, -source, seesVariable(viewer, capability));
    }

    /**
     * A new direct source of an exporter is seen by everybody who sees a capability of the exporter that uses the
     * package, and a package the bundle exports is provided to whoever requires it.
     */
    private void propagateDirect(ResolverResource owner, String name, ResolverCapability capability) {
        int source = direct.get(owner).get(name).get(capability);
        for (SeenUse watcher : new ArrayList<>(watchers.getOrDefault(owner, Collections.<String, List<SeenUse>> emptyMap())
                .getOrDefault(name, Collections.<SeenUse> emptyList()))) {
            seen(watcher.sees, source, watcher.viewer, capability);
        }
        if (!exported.getOrDefault(owner, Collections.<String> emptySet()).contains(name)) {
            return;
        }
        for (ResolverResource user : new ArrayList<>(bundleUsers.getOrDefault(owner, Collections.<ResolverResource> emptyList()))) {
            required(bundleViews.get(user).get(owner), source, user, name, capability);
        }
    }

    /**
     * The user of a bundle sees a package the bundle provides directly, unless the user imports the package itself.
     */
    private void required(int bundle, int provided, ResolverResource user, String name, ResolverCapability capability) {
        int imports = importedVariable(user, name);
        int seen = directVariable(user, name, capability);
        if (seenClauses.add(Arrays.asList(-bundle, -provided, imports, seen))) {
            clause(-bundle, -provided, imports, seen);
            directTerms.add(seen, bundle, provided, -imports);
        }
    }

    /**
     * Requiring a bundle shows its exported packages as the bundle provides them, and the bundles it reexports.
     */
    private void propagateBundle(ResolverResource user, ResolverResource bundle) {
        int source = bundleViews.get(user).get(bundle);
        Map<String, Map<ResolverCapability, Integer>> known = direct.getOrDefault(bundle, Collections.<String, Map<ResolverCapability, Integer>> emptyMap());
        for (Map.Entry<String, Map<ResolverCapability, Integer>> entry : new ArrayList<>(known.entrySet())) {
            if (!exported.getOrDefault(bundle, Collections.<String> emptySet()).contains(entry.getKey())) {
                continue;
            }
            for (Map.Entry<ResolverCapability, Integer> provider : new ArrayList<>(entry.getValue().entrySet())) {
                required(source, provider.getValue(), user, entry.getKey(), provider.getKey());
            }
        }
        for (ResolverRequirement requirement : bundle.getClassSpaceRequirements()) {
            if (!BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace()) || !BundleNamespace.VISIBILITY_REEXPORT
                    .equals(requirement.getDirectives().get(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE))) {
                continue;
            }
            int attachment = requirement.getOwner() == bundle ? TRUE
                    : attachments.getOrDefault(requirement.getOwner(), Collections.<ResolverResource, Integer> emptyMap()).getOrDefault(bundle, FALSE);
            for (Candidate candidate : usable.getOrDefault(requirement, Collections.<Candidate> emptyList())) {
                int reexported = bundleVariable(user, candidate.getProvider());
                clauseOnce(-source, -wires.get(candidate), not(attachment), reexported);
                bundleTerms.add(reexported, attachment == TRUE ? new int[] { source, wires.get(candidate) }
                        : new int[] { source, wires.get(candidate), attachment });
            }
        }
    }

    /**
     * The class space rule: for every capability a resource sees, its own direct sources of a
     * package the capability uses and the exporter's direct sources must be compatible, one set of exporters contains
     * the other. A pair of a direct exporter a and an exporter b the exporter sees that differ is a witness against
     * that unless a is among the exporter's sources as well or b among the resource's, or the package is split over
     * bundles the resource requires (or over bundles that require each other). The resource's direct sources must be
     * one such class space among themselves as well.
     */
    private void conflicts() {
        for (Map.Entry<ResolverResource, Map<String, Map<ResolverCapability, Integer>>> byResource : direct.entrySet()) {
            ResolverResource resource = byResource.getKey();
            if (resource.isFragment()) {
                // a fragment shares the class space of its host, which is checked with the host's own view
                continue;
            }
            for (Map.Entry<String, Map<ResolverCapability, Integer>> byPackage : byResource.getValue().entrySet()) {
                Map<ResolverResource, Integer> owners = ownerCache.owners(byPackage.getValue());
                for (Map.Entry<ResolverResource, Integer> first : owners.entrySet()) {
                    for (Map.Entry<ResolverResource, Integer> second : owners.entrySet()) {
                        if (first.getKey() != second.getKey()) {
                            conflict(FALSE, first.getValue(), second.getValue(), FALSE, FALSE, resource, first.getKey(), second.getKey());
                        }
                    }
                }
            }
        }
        for (Map.Entry<ResolverResource, Map<String, List<SeenUse>>> byExporter : watchers.entrySet()) {
            ResolverResource exporter = byExporter.getKey();
            for (Map.Entry<String, List<SeenUse>> byPackage : byExporter.getValue().entrySet()) {
                Map<ResolverCapability, Integer> exporterSources = direct.getOrDefault(exporter, Collections.<String, Map<ResolverCapability, Integer>> emptyMap()).get(byPackage.getKey());
                if (exporterSources == null) {
                    continue;
                }
                Map<ResolverResource, Integer> implied = ownerCache.owners(exporterSources);
                for (SeenUse watcher : byPackage.getValue()) {
                    if (watcher.viewer.isFragment()) {
                        continue;
                    }
                    Map<ResolverCapability, Integer> viewerSources = direct.getOrDefault(watcher.viewer, Collections.<String, Map<ResolverCapability, Integer>> emptyMap()).get(byPackage.getKey());
                    if (viewerSources == null) {
                        // implied packages only constrain what the resource imports, exports or requires itself
                        continue;
                    }
                    Map<ResolverResource, Integer> seen = ownerCache.owners(viewerSources);
                    for (Map.Entry<ResolverResource, Integer> chosen : seen.entrySet()) {
                        for (Map.Entry<ResolverResource, Integer> other : implied.entrySet()) {
                            if (chosen.getKey() != other.getKey()) {
                                conflict(watcher.sees, chosen.getValue(), other.getValue(),
                                        implied.getOrDefault(chosen.getKey(), FALSE), seen.getOrDefault(other.getKey(), FALSE),
                                        watcher.viewer, chosen.getKey(), other.getKey());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Forbids the pair unless one of the escapes holds: the chosen exporter is among the other side as well, the other
     * exporter is among the chosen side as well, or the pair is a split package of the resource.
     */
    private void conflict(int sees, int chosen, int other, int chosenAlsoOther, int otherAlsoChosen,
            ResolverResource resource, ResolverResource chosenOwner, ResolverResource otherOwner) {
        int[] split = split(resource, chosenOwner, otherOwner);
        int seesLiteral = -sees;
        if (split == null) {
            clause(seesLiteral, -chosen, -other, chosenAlsoOther, otherAlsoChosen);
        } else {
            for (int condition : split) {
                clause(seesLiteral, -chosen, -other, chosenAlsoOther, otherAlsoChosen, condition);
            }
        }
    }

    /**
     * Two exporters of one package are one class space for a resource if it requires both bundles (or is one of them
     * itself), which depends on the wires chosen, or if one exporter requires the other.
     *
     * @return the literals that all have to hold for the pair to be legal, empty if it always is, {@code null} if never
     */
    private int[] split(ResolverResource resource, ResolverResource first, ResolverResource second) {
        Map<ResolverResource, Integer> required = bundleViews.getOrDefault(resource, Collections.<ResolverResource, Integer> emptyMap());
        Integer requiresFirst = first == resource ? Integer.valueOf(TRUE) : required.get(first);
        Integer requiresSecond = second == resource ? Integer.valueOf(TRUE) : required.get(second);
        if (requiresFirst != null && requiresSecond != null) {
            return new int[] { requiresFirst, requiresSecond };
        }
        if (requires(first, second) || requires(second, first)) {
            return new int[0];
        }
        return null;
    }

    private boolean requires(ResolverResource user, ResolverResource bundle) {
        for (ResolverRequirement requirement : user.getResolverRequirements()) {
            if (BundleNamespace.BUNDLE_NAMESPACE.equals(requirement.getNamespace())) {
                for (Candidate candidate : usable.getOrDefault(requirement, Collections.<Candidate> emptyList())) {
                    if (candidate.getProvider() == bundle) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String packageName(ResolverCapability capability) {
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(capability.getNamespace())) {
            return null;
        }
        Object name = capability.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
        return name == null ? null : String.valueOf(name);
    }
}
