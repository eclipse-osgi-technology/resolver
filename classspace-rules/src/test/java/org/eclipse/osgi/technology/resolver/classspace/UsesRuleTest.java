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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.osgi.technology.resolver.classspace.Scenario.uses;

import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * A resource must see a package from the same provider as the exporter of
 * everything it sees that uses the package.
 */
class UsesRuleTest {

    private final Scenario scenario = new Scenario();

    /** The exporter a imports q and exports p that uses q, b imports p and q. */
    private void exporterOfPThatUsesQ() {
        scenario.bundle("q1").addExportPackage("q", new Attrs());
        scenario.bundle("q2").addExportPackage("q", new Attrs());
        scenario.bundle("a").addExportPackage("p", uses("q"));
        scenario.bundle("a").addImportPackage("q", new Attrs());
        scenario.bundle("b").addImportPackage("p", new Attrs());
        scenario.bundle("b").addImportPackage("q", new Attrs());
    }

    @Test
    void aClassSpaceThatSeesAPackageFromTheProviderOfTheExporterIsConsistent() {
        exporterOfPThatUsesQ();
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q1");

        assertThat(scenario.usesRule().check(scenario.resource("b"))).isEmpty();
    }

    @Test
    void aPackageSeenFromAnotherProviderThanTheExporterUsesIsReported() {
        exporterOfPThatUsesQ();
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");

        Optional<UsesViolation> violation = scenario.usesRule().check(scenario.resource("b"));

        assertThat(violation).isPresent();
        assertThat(violation.get().getPackageName()).isEqualTo("q");
        assertThat(violation.get().getConflicting()).extracting(ResolverCapability::getOwner)
                .containsExactlyInAnyOrder(scenario.resource("q1"), scenario.resource("q2"));
    }

    @Test
    void aResourceIsNotConstrainedByAPackageItDoesNotSee() {
        exporterOfPThatUsesQ();
        scenario.bundle("c").addImportPackage("p", new Attrs());
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q1");
        scenario.select("c", "p", "a");

        assertThat(scenario.usesRule().check(scenario.resource("c"))).isEmpty();
    }

    @Test
    void theUsesDirectiveIsFollowedThroughSeveralExporters() {
        scenario.bundle("q1").addExportPackage("q", new Attrs());
        scenario.bundle("q2").addExportPackage("q", new Attrs());
        scenario.bundle("inner").addExportPackage("r", uses("q"));
        scenario.bundle("inner").addImportPackage("q", new Attrs());
        scenario.bundle("outer").addExportPackage("p", uses("r"));
        scenario.bundle("outer").addImportPackage("r", new Attrs());
        scenario.bundle("user").addImportPackage("p", new Attrs());
        scenario.bundle("user").addImportPackage("q", new Attrs());
        scenario.select("inner", "q", "q1");
        scenario.select("outer", "r", "inner");
        scenario.select("user", "p", "outer");
        scenario.select("user", "q", "q2");

        Optional<UsesViolation> violation = scenario.usesRule().check(scenario.resource("user"));

        assertThat(violation).isPresent();
        assertThat(violation.get().getPackageName()).isEqualTo("q");
    }

    @Test
    void aPackageImportedFromTwoProvidersIsReported() {
        scenario.bundle("one").addExportPackage("p", Attrs.create("version", "1.0"));
        scenario.bundle("two").addExportPackage("p", Attrs.create("version", "2.0"));
        scenario.bundle("importer").addImportPackage("p", Attrs.create("version", "[1,2)"));
        scenario.bundle("importer").addImportPackage("p", Attrs.create("version", "[2,3)"));
        scenario.resource("importer").getResolverRequirements().get(0).getCandidates().get(0).select("selected");
        scenario.resource("importer").getResolverRequirements().get(1).getCandidates().get(0).select("selected");

        Optional<UsesViolation> violation = scenario.usesRule().check(scenario.resource("importer"));

        assertThat(violation).isPresent();
        assertThat(violation.get().getPackageName()).isEqualTo("p");
    }
}
