/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.conflicts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.osgi.technology.resolver.conflicts.Scenario.uses;

import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.eclipse.osgi.technology.resolver.candidates.UsesViolation;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * Asking for the violations of many resources at once gives the same answer as
 * asking for each one, without the resources that are not resolved.
 */
class UsesSessionTest {

    private final Scenario scenario = new Scenario();

    /**
     * b sees q from q2 directly and from q1 through a, c has a consistent view.
     */
    private UsesSession inconsistentSelection() {
        scenario.bundle("q1").addExportPackage("q", Attrs.create("version", "1.0.0"));
        scenario.bundle("q2").addExportPackage("q", Attrs.create("version", "2.0.0"));
        scenario.bundle("a").addExportPackage("p", uses("q"));
        scenario.bundle("a").addImportPackage("q", Attrs.create("version", "[1,2)"));
        scenario.bundle("b").addImportPackage("p", new Attrs());
        scenario.bundle("b").addImportPackage("q", Attrs.create("version", "[2,3)"));
        scenario.bundle("c").addImportPackage("q", Attrs.create("version", "[2,3)"));
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");
        scenario.select("c", "q", "q2");
        return scenario.session();
    }

    @Test
    void onlyTheResourcesWithAViolationAreReported() {
        UsesSession session = inconsistentSelection();

        Map<ResolverResource, UsesViolation> violations = session.violationsOf(session.getResources());

        assertThat(violations).containsOnlyKeys(scenario.resource("b"));
        assertThat(violations.get(scenario.resource("b")).getPackageName()).isEqualTo("q");
    }

    @Test
    void aResourceThatFailedIsNotChecked() {
        UsesSession session = inconsistentSelection();
        session.fail(scenario.resource("b"), session.violationOf(scenario.resource("b")).get());

        assertThat(session.violationsOf(session.getResources())).isEmpty();
    }
}
