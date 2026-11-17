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

import java.util.Optional;

import org.eclipse.osgi.technology.resolver.candidates.ResolverCapability;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * A bundle that exports and imports the same package keeps its export only if
 * the import is wired to that export, anybody wired to a discarded export has
 * an inconsistent class space.
 */
class SubstitutionRuleTest {

    private final Scenario scenario = new Scenario();

    /**
     * The bundle sub exports and imports p, provider exports p, user imports p.
     */
    private void substitutableExport() {
        scenario.bundle("provider").addExportPackage("p", new Attrs());
        scenario.bundle("sub").addExportPackage("p", new Attrs());
        scenario.bundle("sub").addImportPackage("p", new Attrs());
        scenario.bundle("user").addImportPackage("p", new Attrs());
    }

    @Test
    void anImportOfADiscardedExportIsReported() {
        substitutableExport();
        scenario.select("sub", "p", "provider");
        scenario.select("user", "p", "sub");

        Optional<UsesViolation> violation = scenario.substitutionRule().check(scenario.resource("user"));

        assertThat(violation).isPresent();
        assertThat(violation.get().getPackageName()).isEqualTo("p");
        assertThat(violation.get().getHiddenImport()).containsSame(scenario.importOf("user", "p"));
        assertThat(violation.get().getConflicting()).extracting(ResolverCapability::getOwner)
                .containsExactly(scenario.resource("sub"), scenario.resource("provider"));
    }

    @Test
    void anExportThatTheBundleImportsItselfIsKept() {
        substitutableExport();
        scenario.select("sub", "p", "sub");
        scenario.select("user", "p", "sub");

        assertThat(scenario.substitutionRule().check(scenario.resource("user"))).isEmpty();
    }

    @Test
    void theBundleThatSubstitutesIsNotAffectedByItsOwnDecision() {
        substitutableExport();
        scenario.select("sub", "p", "provider");

        assertThat(scenario.substitutionRule().check(scenario.resource("sub"))).isEmpty();
    }

    @Test
    void withoutAnExportThatIsAlsoImportedThereIsNothingToCheck() {
        scenario.bundle("provider").addExportPackage("p", new Attrs());
        scenario.bundle("user").addImportPackage("p", new Attrs());
        scenario.select("user", "p", "provider");

        assertThat(scenario.substitutionRule().check(scenario.resource("user"))).isEmpty();
    }
}
