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

import java.util.List;

import org.junit.jupiter.api.Test;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * Hand written cases with a known verdict.
 */
class ConsistencyVerifierTest {

    private static void assertVerdict(Universe u, boolean consistent) {
        List<String> violations = new ConsistencyVerifier(u.wiring()).violations();
        assertThat(violations.isEmpty()).as("violations: %s", violations).isEqualTo(consistent);
    }

    @Test
    void plainImportIsConsistent() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        u.wire("b", "package", "p", "a");
        assertVerdict(u, true);
    }

    @Test
    void usesConflictIsDetected() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder q2 = u.bundle("q2");
        q2.addExportPackage("q", new Attrs());
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        b.addImportPackage("q", new Attrs());
        u.wire("a", "package", "q", "q1");
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "q", "q2");
        assertVerdict(u, false);
    }

    @Test
    void usesAgreementIsConsistent() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        b.addImportPackage("q", new Attrs());
        u.wire("a", "package", "q", "q1");
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "q", "q1");
        assertVerdict(u, true);
    }

    @Test
    void transitiveUsesConflictIsDetected() {
        Universe u = new Universe();
        ResourceBuilder r1 = u.bundle("r1");
        r1.addExportPackage("r", new Attrs());
        ResourceBuilder r2 = u.bundle("r2");
        r2.addExportPackage("r", new Attrs());
        ResourceBuilder m = u.bundle("m");
        m.addExportPackage("q", uses("r"));
        m.addImportPackage("r", new Attrs());
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        b.addImportPackage("r", new Attrs());
        u.wire("m", "package", "r", "r1");
        u.wire("a", "package", "q", "m");
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "r", "r2");
        assertVerdict(u, false);
    }

    @Test
    void usedPackageNotVisibleToConsumerIsIgnored() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        u.wire("b", "package", "p", "a");
        assertVerdict(u, true);
    }

    @Test
    void requireBundleSourcesAreChecked() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder q2 = u.bundle("q2");
        q2.addExportPackage("q", new Attrs());
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addRequireBundle("a", new Attrs());
        b.addImportPackage("q", new Attrs());
        u.wire("a", "package", "q", "q1");
        u.wire("b", "bundle", "a", "a");
        u.wire("b", "package", "q", "q2");
        assertVerdict(u, false);
    }

    @Test
    void reexportedBundleSourcesAreChecked() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder q2 = u.bundle("q2");
        q2.addExportPackage("q", new Attrs());
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder mid = u.bundle("mid");
        mid.addRequireBundle("a", Attrs.create("visibility:", "reexport"));
        ResourceBuilder b = u.bundle("b");
        b.addRequireBundle("mid", new Attrs());
        b.addImportPackage("q", new Attrs());
        u.wire("a", "package", "q", "q1");
        u.wire("mid", "bundle", "a", "a");
        u.wire("b", "bundle", "mid", "mid");
        u.wire("b", "package", "q", "q2");
        assertVerdict(u, false);
    }

    @Test
    void cyclicUsesIsConsistent() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", uses("q"));
        a.addImportPackage("q", new Attrs());
        ResourceBuilder c = u.bundle("c");
        c.addExportPackage("q", uses("p"));
        c.addImportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", new Attrs());
        b.addImportPackage("q", new Attrs());
        u.wire("a", "package", "q", "c");
        u.wire("c", "package", "p", "a");
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "q", "c");
        assertVerdict(u, true);
    }

    @Test
    void genericCapabilityUsesConflictIsDetected() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder q2 = u.bundle("q2");
        q2.addExportPackage("q", new Attrs());
        ResourceBuilder svc = u.bundle("svc");
        svc.addProvideCapability("x.ns", capability("x.ns", "n", "q"));
        svc.addImportPackage("q", new Attrs());
        ResourceBuilder c = u.bundle("c");
        c.addRequireCapability("x.ns", "n", new Attrs());
        c.addImportPackage("q", new Attrs());
        u.wire("svc", "package", "q", "q1");
        u.wire("c", "x.ns", "n", "svc");
        u.wire("c", "package", "q", "q2");
        assertVerdict(u, false);
    }

    @Test
    void genericCapabilityUsesAgreementIsConsistent() {
        Universe u = new Universe();
        ResourceBuilder q1 = u.bundle("q1");
        q1.addExportPackage("q", new Attrs());
        ResourceBuilder svc = u.bundle("svc");
        svc.addProvideCapability("x.ns", capability("x.ns", "n", "q"));
        svc.addImportPackage("q", new Attrs());
        ResourceBuilder c = u.bundle("c");
        c.addRequireCapability("x.ns", "n", new Attrs());
        c.addImportPackage("q", new Attrs());
        u.wire("svc", "package", "q", "q1");
        u.wire("c", "x.ns", "n", "svc");
        u.wire("c", "package", "q", "q1");
        assertVerdict(u, true);
    }

    @Test
    void importingOnePackageFromTwoIncompatibleSourcesIsDetected() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", new Attrs());
        ResourceBuilder c = u.bundle("c");
        c.addExportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", Attrs.create("bundle-symbolic-name", "a"));
        b.addImportPackage("p", Attrs.create("bundle-symbolic-name", "c"));
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "p", "c");
        assertVerdict(u, false);
    }

    @Test
    void importingOnePackageFromSubstitutedSourcesIsConsistent() {
        Universe u = new Universe();
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("p", new Attrs());
        ResourceBuilder c = u.bundle("c");
        c.addExportPackage("p", new Attrs());
        c.addImportPackage("p", Attrs.create("bundle-symbolic-name", "a"));
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("p", Attrs.create("bundle-symbolic-name", "a"));
        b.addImportPackage("p", Attrs.create("bundle-symbolic-name", "c"));
        u.wire("c", "package", "p", "a");
        u.wire("b", "package", "p", "a");
        u.wire("b", "package", "p", "c");
        assertVerdict(u, true);
    }

    // Real world bundles export one package in several versions (e.g. javax.servlet 2.6, 3.0 and 3.1), one bundle has one class loader.
    @Test
    void differentExportsOfOnePackageByOneBundleAreTheSameSource() {
        Universe u = new Universe();
        ResourceBuilder s = u.bundle("s");
        s.addExportPackage("p", Attrs.create("version", "2.6"));
        s.addExportPackage("p", Attrs.create("version", "3.0"));
        ResourceBuilder a = u.bundle("a");
        a.addExportPackage("q", uses("p"));
        a.addImportPackage("p", new Attrs());
        ResourceBuilder b = u.bundle("b");
        b.addImportPackage("q", new Attrs());
        b.addImportPackage("p", new Attrs());
        u.wire("a", "package", "p", "s", 1);
        u.wire("b", "package", "q", "a");
        u.wire("b", "package", "p", "s", 0);
        assertVerdict(u, true);
    }
}
