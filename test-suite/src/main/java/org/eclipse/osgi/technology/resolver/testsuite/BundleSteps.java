/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.testsuite;

import aQute.bnd.header.Attrs;
import io.cucumber.java.en.Given;

/**
 * Cucumber glue steps to describe bundles. A bundle is started with "there is
 * bundle", all following steps starting with "it" describe that bundle until
 * the next bundle is started or a resolve is performed. Every step describes
 * one complete clause of the manifest.
 */
public class BundleSteps {

    private final TestWorld world;
    private BundleBuilder current;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public BundleSteps(TestWorld world) {
        this.world = world;
    }

    private BundleBuilder bundle() {
        if (current == null) {
            throw new IllegalStateException("There is no bundle yet, start one with 'there is bundle \"...\"'");
        }
        return current;
    }

    /**
     * Starts a bundle.
     *
     * @param ref the symbolic name and optionally the version
     */
    @Given("there is {bundle}")
    public void thereIsBundle(BundleRef ref) {
        current = world.startBundle(ref);
    }

    /**
     * Exports a package without version.
     *
     * @param name the package name
     */
    @Given("it exports package {string}")
    public void itExportsPackage(String name) {
        bundle().addExportPackage(name, new Attrs());
    }

    /**
     * Exports a package in a version.
     *
     * @param name    the package name
     * @param version the version of the package
     */
    @Given("it exports package {string} in version {string}")
    public void itExportsPackageInVersion(String name, String version) {
        bundle().addExportPackage(name, new Attrs().with("version", version));
    }

    /**
     * Imports a package of any version.
     *
     * @param name the package name
     */
    @Given("it imports package {string}")
    public void itImportsPackage(String name) {
        bundle().addImportPackage(name, new Attrs());
    }

    /**
     * Imports a package in a version range.
     *
     * @param name  the package name
     * @param range the version range, a plain version means that version or
     *              higher
     */
    @Given("it imports package {string} in version range {string}")
    public void itImportsPackageInRange(String name, String range) {
        bundle().addImportPackage(name, new Attrs().with("version", range));
    }
}
