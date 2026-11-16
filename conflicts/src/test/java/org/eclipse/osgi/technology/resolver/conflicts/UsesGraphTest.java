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

import java.util.Collections;
import java.util.List;

import org.eclipse.osgi.technology.resolver.index.UsesIndex;
import org.junit.jupiter.api.Test;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.resource.Requirement;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;

/**
 * The graph connects the choice for an import to the choices that decide if it
 * is consistent, so a resolver that changes one choice knows what to look at
 * again.
 */
class UsesGraphTest {

    private final Scenario universe = new Scenario();

    private UsesIndex index;

    private UsesGraph graph() {
        index = new UsesIndex(universe.resources(), universe.candidates(), r -> true);
        return new UsesGraph(index, universe.candidates());
    }

    private Requirement importOf(String bundle, String packageName) {
        return index.importsOf(universe.resource(bundle), packageName).get(0);
    }

    /**
     * Two providers of q, a exports p that uses q, b imports p.
     */
    private void bundleThatExportsPUsingQ() {
        universe.bundle("q1").addExportPackage("q", new Attrs());
        universe.bundle("q2").addExportPackage("q", new Attrs());
        universe.bundle("a").addExportPackage("p", Attrs.create("uses:", "q"));
        universe.bundle("a").addImportPackage("q", new Attrs());
        universe.bundle("b").addImportPackage("p", new Attrs());
    }

    @Test
    void anImportDependsOnTheImportThatDecidesWhereAUsedPackageComesFrom() {
        bundleThatExportsPUsingQ();

        UsesGraph graph = graph();

        Requirement p = importOf("b", "p");
        Requirement q = importOf("a", "q");
        assertThat(graph.dependencies(p)).containsExactly(q);
        assertThat(graph.dependents(q)).containsExactly(p);
        assertThat(graph.dependencies(q)).isEmpty();
    }

    @Test
    void aUsesChainThatCanNotBecomeInconsistentHasNoEdges() {
        universe.bundle("only").addExportPackage("q", new Attrs());
        universe.bundle("a").addExportPackage("p", Attrs.create("uses:", "q"));
        universe.bundle("a").addImportPackage("q", new Attrs());
        universe.bundle("b").addImportPackage("p", new Attrs());

        UsesGraph graph = graph();

        assertThat(graph.dependencies(importOf("b", "p"))).isEmpty();
        assertThat(graph.dependents(importOf("a", "q"))).isEmpty();
    }

    @Test
    void everyImportThatCanBeAffectedByAChangeIsFoundOnce() {
        bundleThatExportsPUsingQ();
        universe.bundle("b").addExportPackage("r", Attrs.create("uses:", "p"));
        universe.bundle("c").addImportPackage("r", new Attrs());
        universe.bundle("d").addImportPackage("p", new Attrs());
        universe.bundle("d").addImportPackage("r", new Attrs());

        UsesGraph graph = graph();

        Requirement q = importOf("a", "q");
        Requirement p = importOf("b", "p");
        Requirement r = importOf("c", "r");
        List<Requirement> affected = graph.affected(Collections.singletonList(q));
        assertThat(affected).startsWith(q, p).contains(r, importOf("d", "p"), importOf("d", "r"));
        assertThat(affected).doesNotHaveDuplicates();
        assertThat(graph.affected(Collections.singletonList(r))).containsExactly(r);
    }

    @Test
    void theChoicesAnImportDependsOnAreReachableTransitively() {
        bundleThatExportsPUsingQ();
        universe.bundle("b").addExportPackage("r", Attrs.create("uses:", "p"));
        universe.bundle("c").addImportPackage("r", new Attrs());

        UsesGraph graph = graph();

        assertThat(graph.reachable(importOf("c", "r"))).containsExactly(importOf("c", "r"), importOf("b", "p"),
                importOf("a", "q"));
    }

    @Test
    void aRequiredBundleDependsOnTheImportsBehindItsExports() {
        universe.bundle("lib1").addExportPackage("p", new Attrs());
        universe.bundle("lib2").addExportPackage("p", new Attrs());
        universe.bundle("api").addCapability(new CapReqBuilder(BundleNamespace.BUNDLE_NAMESPACE)
                .addAttribute(BundleNamespace.BUNDLE_NAMESPACE, "api"));
        universe.bundle("api").addExportPackage("p", new Attrs());
        universe.bundle("api").addImportPackage("p", new Attrs());
        universe.bundle("user").addRequirement(new CapReqBuilder(BundleNamespace.BUNDLE_NAMESPACE)
                .addDirective("filter", "(" + BundleNamespace.BUNDLE_NAMESPACE + "=api)"));

        UsesGraph graph = graph();

        Requirement required = index.bundleRequirements(universe.resource("user")).get(0);
        assertThat(graph.dependencies(required)).containsExactly(importOf("api", "p"));
    }
}
