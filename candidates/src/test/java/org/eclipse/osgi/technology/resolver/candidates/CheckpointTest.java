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

import java.util.Collections;

import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;

/**
 * A strategy that tries a change to the graph to see what the class space rule
 * says about it, and takes the change back.
 */
class CheckpointTest {

    private final Graph graph = new Graph();

    private Candidate[] twoProvidersOfP() {
        graph.bundle("a").addExportPackage("p", new Attrs());
        graph.bundle("b").addImportPackage("p", new Attrs());
        graph.bundle("c").addExportPackage("p", new Attrs());
        Candidate chosen = graph.offer("b", "package", "p", "a");
        Candidate other = graph.offer("b", "package", "p", "c");
        chosen.select("Highest priority");
        other.defer("Deferred");
        return new Candidate[] { chosen, other };
    }

    @Test
    void rollbackBringsBackTheSelectionAndTheStateOfResources() {
        Candidate[] candidates = twoProvidersOfP();
        ResolverResource importer = graph.resource("b");
        importer.markResolved();

        try (Checkpoint attempt = graph.checkpoint()) {
            candidates[0].defer("Tried the other one");
            candidates[1].select("Tried instead");
            importer.markFailed(importer.getResolverRequirements().get(0));
            attempt.rollback();
        }

        assertThat(candidates[0].getState()).isEqualTo(CandidateState.SELECTED);
        assertThat(candidates[0].getReason()).isEqualTo("Highest priority");
        assertThat(candidates[1].isAlternative()).isTrue();
        assertThat(candidates[1].getReason()).isEqualTo("Deferred");
        assertThat(importer.getState()).isEqualTo(ResolveState.RESOLVED);
        assertThat(importer.getFailedRequirement()).isNull();
    }

    @Test
    void rollbackBringsBackTheAttachedFragments() {
        graph.bundle("host");
        graph.fragment("fragment", "host");
        ResolverResource host = graph.resource("host");
        ResolverResource fragment = graph.resource("fragment");
        host.setAttachedFragments(Collections.singletonList(fragment));

        try (Checkpoint attempt = graph.checkpoint()) {
            fragment.detachFrom(host, "Not consistent with the host");
            host.setAttachedFragments(Collections.<ResolverResource> emptyList());
            assertThat(fragment.isDetachedFrom(host)).isTrue();
            attempt.rollback();
        }

        assertThat(host.getAttachedFragments()).containsExactly(fragment);
        assertThat(fragment.isDetachedFrom(host)).isFalse();
    }

    @Test
    void aCheckpointCanBeRolledBackAgainAfterAnotherAttempt() {
        Candidate[] candidates = twoProvidersOfP();

        try (Checkpoint attempt = graph.checkpoint()) {
            candidates[0].defer("First attempt");
            attempt.rollback();
            assertThat(candidates[0].getState()).isEqualTo(CandidateState.SELECTED);

            candidates[0].reject("Second attempt");
            attempt.rollback();
        }

        assertThat(candidates[0].getState()).isEqualTo(CandidateState.SELECTED);
        assertThat(candidates[0].getReason()).isEqualTo("Highest priority");
    }

    @Test
    void aNestedCheckpointOnlyTakesBackWhatWasChangedSinceItWasTaken() {
        Candidate[] candidates = twoProvidersOfP();

        try (Checkpoint outer = graph.checkpoint()) {
            candidates[1].reject("Decided in the outer attempt");
            try (Checkpoint inner = graph.checkpoint()) {
                candidates[0].defer("Tried in the inner attempt");
                inner.rollback();
            }
            assertThat(candidates[0].getState()).isEqualTo(CandidateState.SELECTED);
            assertThat(candidates[1].getState()).isEqualTo(CandidateState.REJECTED);

            outer.rollback();
        }

        assertThat(candidates[1].getState()).isEqualTo(CandidateState.DEFERRED);
    }

    @Test
    void closingWithoutARollbackKeepsTheChanges() {
        Candidate[] candidates = twoProvidersOfP();

        try (Checkpoint attempt = graph.checkpoint()) {
            candidates[0].defer("Replaced");
            candidates[1].select("Selected instead");
        }

        assertThat(candidates[0].getState()).isEqualTo(CandidateState.DEFERRED);
        assertThat(candidates[1].getState()).isEqualTo(CandidateState.SELECTED);
    }
}
