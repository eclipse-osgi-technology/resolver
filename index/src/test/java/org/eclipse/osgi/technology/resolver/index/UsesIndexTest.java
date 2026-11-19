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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * Only a package with more than one provider, or one that leads to such a
 * package through a uses directive, can be part of an inconsistent class space.
 */
class UsesIndexTest {

    private final Universe universe = new Universe();

    private UsesIndex index() {
        return new UsesIndex(universe.resources(), universe.candidates(), r -> true);
    }

    @Test
    void aPackageWithSeveralProvidersIsContested() {
        universe.bundle("one").addExportPackage("p", new Attrs());
        universe.bundle("two").addExportPackage("p", new Attrs());
        universe.bundle("single").addExportPackage("q", new Attrs());

        UsesIndex index = index();

        assertThat(index.isContested("p")).isTrue();
        assertThat(index.isContested("q")).isFalse();
        assertThat(index.isContested("unknown")).isFalse();
    }

    @Test
    void aUsesChainThatLeadsToAContestedPackageIsReaching() {
        universe.bundle("one").addExportPackage("q", new Attrs());
        universe.bundle("two").addExportPackage("q", new Attrs());
        universe.bundle("inner").addExportPackage("r", Attrs.create("uses:", "q"));
        universe.bundle("outer").addExportPackage("p", Attrs.create("uses:", "r"));
        universe.bundle("unrelated").addExportPackage("s", Attrs.create("uses:", "t"));
        universe.bundle("other").addExportPackage("t", new Attrs());

        UsesIndex index = index();

        assertThat(index.reachesContested("q")).isTrue();
        assertThat(index.reachesContested("r")).as("uses a contested package").isTrue();
        assertThat(index.reachesContested("p")).as("uses a package that uses a contested one").isTrue();
        assertThat(index.reachesContested("s")).isFalse();
        assertThat(index.reachesContested(Arrays.asList("s", "t"))).isFalse();
        assertThat(index.reachesContested(Arrays.asList("s", "p"))).isTrue();
    }

    @Test
    void theNamesReachingAPackageAreTheOnesThatUseItDirectlyOrNot() {
        universe.bundle("inner").addExportPackage("q", Attrs.create("uses:", "r"));
        universe.bundle("middle").addExportPackage("p", Attrs.create("uses:", "q"));
        universe.bundle("outer").addExportPackage("o", Attrs.create("uses:", "p"));
        universe.bundle("leaf").addExportPackage("r", new Attrs());
        universe.bundle("unrelated").addExportPackage("s", Attrs.create("uses:", "t"));
        universe.bundle("other").addExportPackage("t", new Attrs());

        UsesIndex index = index();

        assertThat(index.reachingFrom(Arrays.asList("q"))).containsExactlyInAnyOrder("q", "p", "o");
        assertThat(index.reachingFrom(Arrays.asList("r"))).containsExactlyInAnyOrder("r", "q", "p", "o");
        assertThat(index.reachingFrom(Arrays.asList("unknown"))).containsExactly("unknown");
    }

    @Test
    void importersAreTheResourcesThatCanBeWiredToAProvider() {
        universe.bundle("provider").addExportPackage("p", new Attrs());
        universe.bundle("importer").addImportPackage("p", new Attrs());
        universe.bundle("bystander").addImportPackage("missing", new Attrs());

        UsesIndex index = index();

        assertThat(index.importers(universe.resource("provider"))).containsExactly(universe.resource("importer"));
        assertThat(index.importers(universe.resource("importer"))).isEmpty();
        assertThat(index.importsOf(universe.resource("importer"), "p")).hasSize(1);
    }

    @Test
    void anAlreadyResolvedResourceOnlyContributesItsCapabilities() {
        universe.bundle("one").addExportPackage("p", new Attrs());
        universe.bundle("two").addExportPackage("p", new Attrs());
        universe.bundle("resolved").addImportPackage("p", new Attrs());

        UsesIndex index = new UsesIndex(universe.resources(), universe.candidates(),
                r -> r != universe.resource("resolved"));

        assertThat(index.importers(universe.resource("one"))).isEmpty();
    }
}
