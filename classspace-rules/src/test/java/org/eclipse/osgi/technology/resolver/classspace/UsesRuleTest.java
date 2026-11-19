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
import org.eclipse.osgi.technology.resolver.candidates.UsesChain;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * A resource must see a package from the same provider as the exporter of
 * everything it sees that uses the package.
 */
class UsesRuleTest {

    private final Scenario scenario = new Scenario();

    /**
     * The exporter a imports q and exports p that uses q, b imports p and q.
     */
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
    void aViolationTellsWhatTheResourceSeesAndWhatTheCapabilitiesItSeesExpect() {
        exporterOfPThatUsesQ();
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");

        UsesViolation violation = scenario.usesRule()
                .check(scenario.resource("b")).get();

        assertThat(violation.getSeen()).extracting(ResolverCapability::getOwner)
                .containsExactly(scenario.resource("q2"));
        assertThat(violation.getImplied())
                .extracting(ResolverCapability::getOwner)
                .containsExactly(scenario.resource("q1"));
        assertThat(violation.getChains()).extracting(
                chain -> chain.getCapability().getOwner())
                .containsExactly(scenario.resource("a"));
    }

    @Test
    void aChainLeadsThroughTheRequirementsToTheCapabilityThatExpectsAnotherProvider() {
        exporterOfPThatUsesQ();
        scenario.bundle("c").addExportPackage("r", uses("p"));
        scenario.bundle("c").addImportPackage("p", new Attrs());
        scenario.bundle("d").addImportPackage("r", new Attrs());
        scenario.bundle("d").addImportPackage("q", new Attrs());
        scenario.select("a", "q", "q1");
        scenario.select("c", "p", "a");
        scenario.select("d", "r", "c");
        scenario.select("d", "q", "q2");

        UsesViolation violation = scenario.usesRule()
                .check(scenario.resource("d")).get();

        assertThat(violation.getChains()).hasSize(1);
        UsesChain chain = violation.getChains().get(0);
        assertThat(chain.getCapability().getOwner())
                .isSameAs(scenario.resource("a"));
        assertThat(chain.getViewer()).isSameAs(scenario.resource("c"));
        assertThat(chain.getParent().get().getCapability().getOwner())
                .isSameAs(scenario.resource("c"));
        assertThat(chain.getParent().get().getParent()).isEmpty();
        assertThat(chain.getRequirement()).containsSame(scenario.importOf("c", "p"));
        assertThat(chain.getParent().get().getRequirement())
                .containsSame(scenario.importOf("d", "r"));
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

    /**
     * The fragment exports p and spi that use each other, legacy exports both
     * as well, consumer imports p.
     */
    private void fragmentWhoseExportsUseEachOther() {
        scenario.bundle("legacy").addExportPackage("p", new Attrs());
        scenario.bundle("legacy").addExportPackage("spi", new Attrs());
        scenario.bundle("host");
        scenario.fragment("fragment", "host").addExportPackage("p", uses("spi"));
        scenario.fragment("fragment", "host").addExportPackage("spi", uses("p"));
        scenario.bundle("consumer").addImportPackage("p", new Attrs());
        scenario.attach("host", "fragment");
        scenario.selectHosted("consumer", "p", "host", "fragment");
    }

    @Test
    void theExportsOfAFragmentThatUseEachOtherAreOneClassSpaceWithTheHost() {
        fragmentWhoseExportsUseEachOther();

        assertThat(scenario.usesRule().check(scenario.resource("consumer"))).isEmpty();
    }

    @Test
    void aFragmentIsNotCheckedOnItsOwnAsItIsPartOfTheClassSpaceOfItsHost() {
        scenario.bundle("ant").addExportPackage("a", uses("p"));
        scenario.bundle("ant").addImportPackage("p", new Attrs());
        scenario.bundle("host");
        scenario.fragment("fragment", "host").addExportPackage("p", new Attrs());
        scenario.fragment("fragment", "host").addImportPackage("a", new Attrs());
        scenario.attach("host", "fragment");
        scenario.selectHosted("ant", "p", "host", "fragment");
        scenario.select("fragment", "a", "ant");

        assertThat(scenario.usesRule().check(scenario.resource("fragment"))).isEmpty();
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
