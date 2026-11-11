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
import aQute.bnd.header.Parameters;
import io.cucumber.java.en.Given;

/**
 * Cucumber glue steps to describe bundles. A bundle is started with "there is
 * bundle", all following steps starting with "it" describe that bundle until
 * the next bundle is started or a resolve is performed. Every step describes
 * one complete clause of the manifest. Concepts of the specification have
 * their own wording, everything else can be added with a trailing
 * {@code with "<attributes>"} that uses the syntax of a manifest clause, e.g.
 * {@code color=red;mandatory:=color}.
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

    /**
     * Parses the attributes and directives of a manifest clause, e.g.
     * {@code a=b;c:=d}, which OSGiHeader.parseProperties rejects.
     */
    private static Attrs attrs(String clauseAttributes) {
        return new Parameters("x;" + clauseAttributes).get("x");
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
     * Exports a package with attributes and directives.
     *
     * @param name       the package name
     * @param attributes the attributes and directives in the syntax of a
     *                   manifest clause
     */
    @Given("it exports package {string} with {string}")
    public void itExportsPackageWith(String name, String attributes) {
        bundle().addExportPackage(name, attrs(attributes));
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
     * Imports a package with attributes and directives.
     *
     * @param name       the package name
     * @param attributes the attributes and directives in the syntax of a
     *                   manifest clause
     */
    @Given("it imports package {string} with {string}")
    public void itImportsPackageWith(String name, String attributes) {
        bundle().addImportPackage(name, attrs(attributes));
    }

    /**
     * Imports a package optionally, the bundle resolves without an exporter.
     *
     * @param name the package name
     */
    @Given("it optionally imports package {string}")
    public void itOptionallyImportsPackage(String name) {
        bundle().addImportPackage(name, new Attrs().with("resolution:", "optional"));
    }

    /**
     * Imports a package from a specific exporting bundle.
     *
     * @param name   the package name
     * @param bundle the symbolic name of the bundle that has to export it
     */
    @Given("it imports package {string} from bundle {string}")
    public void itImportsPackageFromBundle(String name, String bundle) {
        bundle().addImportPackage(name, new Attrs().with("bundle-symbolic-name", bundle));
    }

    /**
     * Imports a package dynamically, which is only wired on request and not
     * when resolving.
     *
     * @param name the package name
     */
    @Given("it dynamically imports package {string}")
    public void itDynamicallyImportsPackage(String name) {
        bundle().addDynamicImportPackage(name);
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

    /**
     * Requires a bundle of any version.
     *
     * @param name the symbolic name of the required bundle
     */
    @Given("it requires bundle {string}")
    public void itRequiresBundle(String name) {
        bundle().addRequireBundle(name, new Attrs());
    }

    /**
     * Requires a bundle in a version range.
     *
     * @param name  the symbolic name of the required bundle
     * @param range the version range, a plain version means that version or
     *              higher
     */
    @Given("it requires bundle {string} in version range {string}")
    public void itRequiresBundleInRange(String name, String range) {
        bundle().addRequireBundle(name, new Attrs().with("bundle-version", range));
    }

    /**
     * Requires a bundle and makes its exports visible to the bundles that
     * require this one.
     *
     * @param name the symbolic name of the required bundle
     */
    @Given("it requires bundle {string} and reexports it")
    public void itRequiresBundleAndReexportsIt(String name) {
        bundle().addRequireBundle(name, new Attrs().with("visibility:", "reexport"));
    }

    /**
     * Requires a bundle optionally, the bundle resolves without it.
     *
     * @param name the symbolic name of the required bundle
     */
    @Given("it optionally requires bundle {string}")
    public void itOptionallyRequiresBundle(String name) {
        bundle().addRequireBundle(name, new Attrs().with("resolution:", "optional"));
    }
}
