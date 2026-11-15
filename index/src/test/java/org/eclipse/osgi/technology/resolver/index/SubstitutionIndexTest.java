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
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Resource;

import aQute.bnd.header.Attrs;

/**
 * An export that the same bundle imports as well may be discarded, who is
 * affected is known up front.
 */
class SubstitutionIndexTest {

    private final Universe universe = new Universe();

    private SubstitutionIndex index(Resource... certain) {
        return new SubstitutionIndex(universe.resources(), universe.candidates(), r -> true,
                Arrays.asList(certain));
    }

    @Test
    void anExportThatIsImportedByTheSameBundleIsSubstitutable() {
        universe.bundle("sub").addExportPackage("p", new Attrs());
        universe.bundle("sub").addImportPackage("p", new Attrs());
        universe.bundle("user").addImportPackage("p", new Attrs());
        universe.bundle("plain").addExportPackage("q", new Attrs());

        SubstitutionIndex index = index();

        assertThat(index.getSubstitutions()).hasSize(1);
        Substitution substitution = index.getSubstitutions().get(0);
        assertThat(substitution.getExport().getResource()).isSameAs(universe.resource("sub"));
        assertThat(substitution.getOverlappingImport().getResource()).isSameAs(universe.resource("sub"));
        assertThat(substitution.isKeepable()).isTrue();
        assertThat(substitution.getDemand()).as("the requirements of others the export can satisfy")
                .extracting(r -> r.getResource()).containsExactly(universe.resource("user"));
        assertThat(index.getSubstitution(substitution.getExport())).isSameAs(substitution);
        assertThat(index.getSubstitutionOfImport(substitution.getOverlappingImport())).isSameAs(substitution);
        assertThat(index.getSubstitution(universe.resource("plain").getCapabilities("osgi.wiring.package").get(0)))
                .isNull();
    }

    @Test
    void anExportThatDoesNotMatchItsOwnImportCanNotBeKept() {
        universe.bundle("sub").addExportPackage("p", Attrs.create("version", "1.0"));
        universe.bundle("sub").addImportPackage("p", Attrs.create("version", "[2,3)"));
        universe.bundle("other").addExportPackage("p", Attrs.create("version", "2.0"));

        assertThat(index().getSubstitutions().get(0).isKeepable()).isFalse();
    }

    @Test
    void aResourceThatMustBeResolvedAndHasNoAlternativePinsTheExport() {
        universe.bundle("sub").addExportPackage("p", new Attrs());
        universe.bundle("sub").addImportPackage("p", new Attrs());
        universe.bundle("user").addImportPackage("p", new Attrs());

        assertThat(index(universe.resource("user")).getSubstitutions().get(0).isPinned()).isTrue();
        assertThat(index(universe.resource("user")).getSubstitutions().get(0).getPinnedBy().getResource())
                .isSameAs(universe.resource("user"));
        assertThat(index().getSubstitutions().get(0).isPinned()).as("nobody is certain to be resolved").isFalse();
    }

    @Test
    void anAlternativeProviderPreventsPinning() {
        universe.bundle("sub").addExportPackage("p", new Attrs());
        universe.bundle("sub").addImportPackage("p", new Attrs());
        universe.bundle("alternative").addExportPackage("p", new Attrs());
        universe.bundle("user").addImportPackage("p", new Attrs());

        assertThat(index(universe.resource("user")).getSubstitutions().get(0).isPinned()).isFalse();
    }

    @Test
    void withoutSubstitutionsThereIsNothingToKnow() {
        universe.bundle("plain").addExportPackage("p", new Attrs());

        assertThat(new SubstitutionIndex(universe.resources(), universe.candidates(), r -> true,
                Collections.<Resource> emptyList()).getSubstitutions()).isEmpty();
    }
}
