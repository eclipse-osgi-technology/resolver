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

import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * The rules over the resources of a graph, built from the graph alone: a
 * resource is consistent only if neither the substitution nor the uses rule
 * finds a problem.
 */
class ClassSpaceRulesTest {

    private final Scenario scenario = new Scenario();

    @Test
    void aDiscardedExportIsReportedBeforeAnythingElse() {
        scenario.bundle("provider").addExportPackage("p", new Attrs());
        scenario.bundle("sub").addExportPackage("p", new Attrs());
        scenario.bundle("sub").addImportPackage("p", new Attrs());
        scenario.bundle("user").addImportPackage("p", new Attrs());
        scenario.select("sub", "p", "provider");
        scenario.select("user", "p", "sub");

        Optional<UsesViolation> violation = scenario.classSpaceRules().violationOf(scenario.resource("user"));

        assertThat(violation).isPresent();
        assertThat(violation.get().getHiddenImport()).containsSame(scenario.importOf("user", "p"));
    }

    @Test
    void aPackageSeenFromDifferentProvidersIsReported() {
        scenario.bundle("q1").addExportPackage("q", new Attrs());
        scenario.bundle("q2").addExportPackage("q", new Attrs());
        scenario.bundle("a").addExportPackage("p", Scenario.uses("q"));
        scenario.bundle("a").addImportPackage("q", new Attrs());
        scenario.bundle("b").addImportPackage("p", new Attrs());
        scenario.bundle("b").addImportPackage("q", new Attrs());
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");

        ClassSpaceRules rules = scenario.classSpaceRules();

        assertThat(rules.violationOf(scenario.resource("b"))).hasValueSatisfying(
                violation -> assertThat(violation.getPackageName()).isEqualTo("q"));
        assertThat(rules.violationOf(scenario.resource("a"))).isEmpty();
        assertThat(rules.getUsesIndex().isContested("q")).isTrue();
    }

    @Test
    void aConsistentSelectionHasNoViolation() {
        scenario.bundle("q1").addExportPackage("q", new Attrs());
        scenario.bundle("q2").addExportPackage("q", new Attrs());
        scenario.bundle("a").addExportPackage("p", Scenario.uses("q"));
        scenario.bundle("a").addImportPackage("q", new Attrs());
        scenario.bundle("b").addImportPackage("p", new Attrs());
        scenario.bundle("b").addImportPackage("q", new Attrs());
        scenario.select("a", "q", "q2");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");

        assertThat(scenario.classSpaceRules().violationOf(scenario.resource("b"))).isEmpty();
    }
}
