/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.verifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.osgi.technology.resolver.verifier.Universe.capability;
import static org.eclipse.osgi.technology.resolver.verifier.Universe.uses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

import aQute.bnd.osgi.resource.WireImpl;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.ResourceBuilder;

/** Hand written results, well formed and broken in exactly one way. */
class WiringVerifierTest {

    private static List<String> violations(Universe u, Map<Resource, List<Wire>> result, String... mandatory) {
        List<Resource> requested = new ArrayList<>();
        for (String name : mandatory) {
            requested.add(u.resource(name));
        }
        return new WiringVerifier(requested, new ArrayList<Resource>(), result).violations();
    }

    private static Universe simple() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        return u;
    }

    @Test
    void completeWiringIsAccepted() {
        Universe u = simple();
        u.wire("b", "package", "p", "a");
        assertThat(violations(u, u.wiring(), "b")).isEmpty();
    }

    @Test
    void missingWireIsReported() {
        Universe u = simple();
        assertThat(violations(u, u.wiring(), "b")).anyMatch(v -> v.contains("has no wire"));
    }

    @Test
    void missingMandatoryResourceIsReported() {
        Universe u = simple();
        u.wire("b", "package", "p", "a");
        Map<Resource, List<Wire>> result = u.wiring();
        result.remove(u.resource("b"));
        assertThat(violations(u, result, "b")).anyMatch(v -> v.contains("mandatory b is not part"));
    }

    @Test
    void providerOutsideTheResultIsReported() {
        Universe u = simple();
        u.wire("b", "package", "p", "a");
        Map<Resource, List<Wire>> result = u.wiring();
        result.remove(u.resource("a"));
        assertThat(violations(u, result, "b")).anyMatch(v -> v.contains("not part of the result"));
    }

    @Test
    void unrequestedResourceIsReported() {
        Universe u = simple();
        ResourceBuilder c = u.bundle("c");
        u.wire("b", "package", "p", "a");
        assertThat(violations(u, u.wiring(), "b")).anyMatch(v -> v.contains("neither requested"));
    }

    @Test
    void singleCardinalityWithTwoWiresIsReported() {
        Universe u = new Universe();
        ResourceBuilder a1 = u.bundle("a1");
        a1.addExportPackage("p", new Attrs());
        ResourceBuilder a2 = u.bundle("a2");
        a2.addExportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        u.wire("b", "package", "p", "a1").wire("b", "package", "p", "a2");
        assertThat(violations(u, u.wiring(), "b")).anyMatch(v -> v.contains("single cardinality"));
    }

    @Test
    void wireToAnotherNamespaceIsReported() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addProvideCapability("x", capability("x", "p"));
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        Wire wire = new WireImpl(u.resource("a").getCapabilities("x").get(0),
                u.resource("b").getRequirements("osgi.wiring.package").get(0));
        Map<Resource, List<Wire>> result = u.wiring();
        result.get(u.resource("b")).add(wire);
        assertThat(violations(u, result, "b")).anyMatch(v -> v.contains("different namespaces"));
    }
}
