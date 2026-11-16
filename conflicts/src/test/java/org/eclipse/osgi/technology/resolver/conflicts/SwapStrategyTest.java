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

import org.eclipse.osgi.technology.resolver.candidates.Candidate;
import org.eclipse.osgi.technology.resolver.candidates.CandidateState;
import org.eclipse.osgi.technology.resolver.candidates.ResolveState;
import org.eclipse.osgi.technology.resolver.candidates.ResolverRequirement;
import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * A strategy works on the session a resolver hands it: it asks for the
 * violation of a resource, changes the selection through the candidates, asks
 * the graph what a change can affect and fails what it can not repair.
 */
class SwapStrategyTest {

    private final Scenario scenario = new Scenario();
    private final UsesConflictStrategy strategy = new SwapStrategy();

    /**
     * Two providers of q, a exports p that uses q, b imports p and q.
     */
    private void exporterOfPThatUsesQ(String importOfQ) {
        scenario.bundle("q1").addExportPackage("q", Attrs.create("version", "1.0.0"));
        scenario.bundle("q2").addExportPackage("q", Attrs.create("version", "2.0.0"));
        scenario.bundle("a").addExportPackage("p", uses("q"));
        scenario.bundle("a").addImportPackage("q", Attrs.create("version", importOfQ));
        scenario.bundle("b").addImportPackage("p", new Attrs());
        scenario.bundle("b").addImportPackage("q", Attrs.create("version", "[2,3)"));
    }

    @Test
    void aConflictIsRepairedByAnotherChoiceBehindTheUsesChain() {
        exporterOfPThatUsesQ("[1,3)");
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");
        UsesSession session = scenario.session();
        assertThat(session.violationOf(scenario.resource("b"))).isPresent();

        strategy.enforceUses(session);

        assertThat(session.violationOf(scenario.resource("b"))).isEmpty();
        assertThat(scenario.resource("b").getState()).isEqualTo(ResolveState.RESOLVED);
        ResolverRequirement importOfQ = scenario.importOf("a", "q");
        assertThat(importOfQ.getCandidates()).extracting(Candidate::getState).containsExactly(CandidateState.DEFERRED,
                CandidateState.SELECTED);
        assertThat(importOfQ.getCandidates().get(0).getReason()).contains("Replaced by q2");
    }

    @Test
    void aConflictWithoutAnAlternativeFailsTheResourceThatSeesIt() {
        exporterOfPThatUsesQ("[1,2)");
        scenario.select("a", "q", "q1");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");
        UsesSession session = scenario.session();

        strategy.enforceUses(session);

        assertThat(scenario.resource("b").getState()).isEqualTo(ResolveState.FAILED);
        assertThat(scenario.resource("b").getUsesViolation().getPackageName()).isEqualTo("q");
        assertThat(scenario.resource("a").getState()).isEqualTo(ResolveState.RESOLVED);
    }

    @Test
    void aConsistentSelectionIsLeftAlone() {
        exporterOfPThatUsesQ("[1,3)");
        scenario.select("a", "q", "q2");
        scenario.select("b", "p", "a");
        scenario.select("b", "q", "q2");
        UsesSession session = scenario.session();

        strategy.enforceUses(session);

        assertThat(scenario.resource("a").getState()).isEqualTo(ResolveState.RESOLVED);
        assertThat(scenario.resource("b").getState()).isEqualTo(ResolveState.RESOLVED);
        assertThat(scenario.importOf("a", "q").getCandidates()).extracting(Candidate::getState)
                .containsExactly(CandidateState.DEFERRED, CandidateState.SELECTED);
    }
}
