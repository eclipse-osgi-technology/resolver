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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.osgi.framework.namespace.BundleNamespace;

import aQute.bnd.header.Attrs;

/**
 * Which packages a resource sees for the providers that are selected, the
 * input every rule about the class space needs.
 */
class ClassSpaceTest {

    private final Graph graph = new Graph();

    private static Attrs reexport() {
        return Attrs.create(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE + ":", BundleNamespace.VISIBILITY_REEXPORT);
    }

    private static Attrs version(String range) {
        return Attrs.create("version", range);
    }

    private static List<ResolverResource> owners(List<ResolverCapability> capabilities) {
        return capabilities.stream().map(ResolverCapability::getOwner).collect(java.util.stream.Collectors.toList());
    }

    @Test
    void anExportIsSeenUnlessThePackageIsImportedToo() {
        graph.bundle("exporter").addExportPackage("p", new Attrs());
        graph.bundle("substituting").addExportPackage("p", new Attrs());
        graph.bundle("substituting").addImportPackage("p", new Attrs());
        graph.bundle("provider").addExportPackage("p", new Attrs());

        assertThat(graph.resource("exporter").getPackageSources("p").get(0).getOwner()).isSameAs(graph.resource("exporter"));

        Candidate provided = graph.offer("substituting", "package", "p", "provider");
        assertThat(graph.resource("substituting").getPackageSources("p").get(0).getOwner())
                .as("the own export while no provider is selected").isSameAs(graph.resource("substituting"));

        provided.select("selected");
        assertThat(graph.resource("substituting").getPackageSources("p").get(0).getOwner())
                .as("the selected provider replaces the export").isSameAs(graph.resource("provider"));
    }

    @Test
    void aChangeOfAProvidersImportsIsSeenByTheRequirer() {
        graph.bundle("origin").addExportPackage("p", new Attrs());
        graph.bundle("other").addExportPackage("p", new Attrs());
        graph.bundle("provider").addExportPackage("p", new Attrs());
        graph.bundle("provider").addImportPackage("p", new Attrs());
        graph.bundle("requirer").addRequireBundle("provider", new Attrs());
        graph.select("requirer", "bundle", "provider", "provider");
        Candidate chosen = graph.select("provider", "package", "p", "origin");
        Candidate alternative = graph.offer("provider", "package", "p", "other");

        assertThat(owners(graph.resource("requirer").getPackageSources("p")))
                .containsExactly(graph.resource("origin"));

        chosen.defer("another provider is preferred");
        alternative.select("selected");
        assertThat(owners(graph.resource("requirer").getPackageSources("p")))
                .as("the view of the requirer follows the selection of the provider")
                .containsExactly(graph.resource("other"));
    }

    @Test
    void aPackageImportedFromTwoProvidersIsAConflict() {
        graph.bundle("one").addExportPackage("p", version("1.0"));
        graph.bundle("two").addExportPackage("p", version("2.0"));
        graph.bundle("importer").addImportPackage("p", version("[1,2)"));
        graph.bundle("importer").addImportPackage("p", version("[2,3)"));
        graph.offer("importer", "package", "p", "one", "(version>=1.0.0)").select("selected");
        graph.offer("importer", "package", "p", "two", "(version>=2.0.0)(!").select("selected");

        assertThat(owners(graph.resource("importer").getConflictingImports()))
                .containsExactly(graph.resource("one"), graph.resource("two"));
    }

    @Test
    void aPackageImportedTwiceFromTheSameProviderIsNoConflict() {
        graph.bundle("one").addExportPackage("p", version("1.0"));
        graph.bundle("importer").addImportPackage("p", version("[1,3)"));
        graph.bundle("importer").addImportPackage("p", version("[1,2)"));
        graph.offer("importer", "package", "p", "one", "3.0.0").select("selected");
        graph.offer("importer", "package", "p", "one", "2.0.0").select("selected");

        assertThat(graph.resource("importer").getConflictingImports()).isEmpty();
    }

    @Test
    void requiredBundlesGiveTheirPackagesAndOnlyReexportedOnesPass() {
        graph.bundle("base").addExportPackage("base.api", new Attrs());
        graph.bundle("middle").addExportPackage("middle.api", new Attrs());
        graph.bundle("middle").addRequireBundle("base", reexport());
        graph.bundle("hidden").addExportPackage("hidden.api", new Attrs());
        graph.bundle("hidden").addRequireBundle("base", new Attrs());
        graph.bundle("client").addRequireBundle("middle", new Attrs());
        graph.bundle("other").addRequireBundle("hidden", new Attrs());
        graph.select("middle", "bundle", "base", "base");
        graph.select("hidden", "bundle", "base", "base");
        graph.select("client", "bundle", "middle", "middle");
        graph.select("other", "bundle", "hidden", "hidden");

        assertThat(graph.resource("client").getRequiredPackages().keySet()).containsExactlyInAnyOrder("middle.api",
                "base.api");
        assertThat(graph.resource("other").getRequiredPackages().keySet()).containsExactly("hidden.api");
    }

    @Test
    void aPackageSplitOverARequiredBundleHasBothSources() {
        graph.bundle("split").addExportPackage("p", new Attrs());
        graph.bundle("user").addExportPackage("p", new Attrs());
        graph.bundle("user").addRequireBundle("split", new Attrs());
        graph.bundle("importing").addImportPackage("p", new Attrs());
        graph.select("user", "bundle", "split", "split");
        graph.select("importing", "package", "p", "user");

        assertThat(owners(graph.resource("user").getPackageSources("p")))
                .containsExactly(graph.resource("user"), graph.resource("split"));
        assertThat(owners(graph.resource("importing").getPackageSources("p")))
                .as("an importer sees the package from its provider and what the provider is split with")
                .containsExactly(graph.resource("user"), graph.resource("split"));
        assertThat(graph.resource("importing").getPackageSources("unknown")).isEmpty();
    }

    @Test
    void aFragmentContributesItsExportsAndImportsToTheHost() {
        graph.bundle("host").addExportPackage("own", new Attrs());
        graph.fragment("fragment", "host").addExportPackage("extra", new Attrs());
        graph.fragment("fragment", "host").addImportPackage("needed", new Attrs());
        graph.bundle("provider").addExportPackage("needed", new Attrs());
        ResolverResource host = graph.resource("host");
        ResolverResource fragment = graph.resource("fragment");
        graph.select("fragment", "host", "host", "host");
        graph.select("fragment", "package", "needed", "provider");
        host.setAttachedFragments(Collections.singletonList(fragment));

        assertThat(fragment.isFragment()).isTrue();
        assertThat(fragment.getHosts()).containsExactly(host);
        assertThat(host.getSeenCapabilities()).filteredOn(c -> "osgi.wiring.package".equals(c.getNamespace()))
                .extracting(c -> c.getAttributes().get("osgi.wiring.package"))
                .containsExactlyInAnyOrder("own", "extra");
        assertThat(host.getClassSpaceRequirements()).as("a fragment that is not resolved yet adds no imports")
                .isEmpty();

        fragment.markResolved();
        assertThat(host.getClassSpaceRequirements()).as("the host requirement of the fragment is not inherited")
                .extracting(ResolverRequirement::getNamespace).containsExactly("osgi.wiring.package");
        assertThat(host.getSeenCapabilities()).filteredOn(c -> "osgi.wiring.package".equals(c.getNamespace()))
                .extracting(c -> c.getAttributes().get("osgi.wiring.package"))
                .containsExactlyInAnyOrder("own", "extra", "needed");

        fragment.detachFrom(host, "Not consistent with the host");
        assertThat(fragment.isDetachedFrom(host)).isTrue();
        assertThat(fragment.getHosts()).as("the host candidate is rejected").isEmpty();
    }

    @Test
    void theUsesDirectiveIsReadAsAListOfPackages() {
        graph.bundle("exporter").addExportPackage("p", Attrs.create("uses:", " q, r ,,s"));
        graph.bundle("plain").addExportPackage("q", new Attrs());

        assertThat(graph.capability("exporter", "package", "p").getUses()).isEqualTo(Arrays.asList("q", "r", "s"));
        assertThat(graph.capability("plain", "package", "q").getUses()).isEmpty();
    }
}
