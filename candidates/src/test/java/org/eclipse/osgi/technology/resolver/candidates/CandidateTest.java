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

import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * What becomes of a candidate and why, as a result is explained from it.
 */
class CandidateTest {

    private final Graph graph = new Graph();

    private ResolverRequirement importOfP() {
        graph.bundle("a").addExportPackage("p", new Attrs());
        graph.bundle("b").addImportPackage("p", new Attrs());
        graph.bundle("c").addExportPackage("p", new Attrs());
        return graph.resource("b").getResolverRequirements().get(0);
    }

    @Test
    void candidatesKeepTheOrderOfPreference() {
        ResolverRequirement requirement = importOfP();
        Candidate second = graph.offer("b", "package", "p", "c");
        Candidate first = requirement.insertCandidate(0, graph.capability("a", "package", "p"),
                "Preferred by the resolve context");

        assertThat(requirement.getCandidates()).containsExactly(first, second);
        assertThat(first.getState()).isEqualTo(CandidateState.AVAILABLE);
        assertThat(first.getReason()).isEqualTo("Preferred by the resolve context");
        assertThat(first.getProvider()).isSameAs(graph.resource("a"));
    }

    @Test
    void aDeferredCandidateIsAnAlternativeUntilItIsRejected() {
        importOfP();
        Candidate chosen = graph.offer("b", "package", "p", "a");
        Candidate other = graph.offer("b", "package", "p", "c");

        chosen.select("Highest priority");
        other.defer("Not considered because a candidate with higher priority is selected");

        assertThat(other.getState()).isEqualTo(CandidateState.DEFERRED);
        assertThat(other.isIncluded()).isFalse();
        assertThat(other.getReason()).startsWith("Not considered");
        assertThat(other.isAlternative()).isTrue();
        assertThat(chosen.isAlternative()).isFalse();
        assertThat(chosen.isIncluded()).isTrue();

        other.reject("Its export is substituted");
        assertThat(other.getState()).isEqualTo(CandidateState.REJECTED);
        assertThat(other.isIncluded()).isFalse();
        assertThat(other.isAlternative()).isFalse();
    }

    @Test
    void restoreBringsBackWhatAnAttemptChanged() {
        importOfP();
        Candidate chosen = graph.offer("b", "package", "p", "a");
        Candidate other = graph.offer("b", "package", "p", "c");
        chosen.select("Highest priority");
        other.defer("Deferred");

        chosen.defer("Switched to the other one");
        other.select("Switched to resolve a conflict");
        other.restore(CandidateState.DEFERRED, "Deferred");
        chosen.restore(CandidateState.SELECTED, "Highest priority");

        assertThat(chosen.getState()).isEqualTo(CandidateState.SELECTED);
        assertThat(chosen.getReason()).isEqualTo("Highest priority");
        assertThat(other.isAlternative()).isTrue();
    }

    @Test
    void aCandidateOfAFailedProviderIsNoAlternative() {
        importOfP();
        graph.offer("b", "package", "p", "a").select("Highest priority");
        Candidate other = graph.offer("b", "package", "p", "c");
        other.defer("Deferred");

        graph.resource("c").markFailed(graph.resource("b").getResolverRequirements().get(0));

        assertThat(other.getFailedResource()).isSameAs(graph.resource("c"));
        assertThat(other.isAlternative()).isFalse();
        assertThat(graph.resource("c").getState()).isEqualTo(ResolveState.FAILED);
    }
}
