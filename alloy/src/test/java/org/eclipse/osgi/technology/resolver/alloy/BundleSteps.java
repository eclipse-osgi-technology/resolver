/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

import org.eclipse.osgi.technology.resolver.testsuite.BundleRef;

import io.cucumber.java.en.Given;

/**
 * The steps that describe bundles, written to the Alloy model.
 * A step the model can not express yet skips the scenario.
 */
public class BundleSteps {

    private final AlloyWorld world;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public BundleSteps(AlloyWorld world) {
        this.world = world;
    }

    /**
     * The step {@code there is {bundle}}.
     *
     * @param ref the bundle
     */
    @Given("there is {bundle}")
    public void thereIs(BundleRef ref) {
        world.startBundle(ref);
    }

    /**
     * The step {@code it exports package {string}}.
     *
     * @param pkg the package name
     */
    @Given("it exports package {string}")
    public void itExportsPackage(String pkg) {
        world.export(pkg, null, null);
    }

    /**
     * The step {@code it exports package {string} in version {string}}.
     *
     * @param pkg the package name
     * @param version the version
     */
    @Given("it exports package {string} in version {string}")
    public void itExportsPackageInVersion(String pkg, String version) {
        world.export(pkg, version, null);
    }

    /**
     * The step {@code it exports package {string} using {string}}.
     *
     * @param pkg the package name
     * @param uses the packages of the uses directive
     */
    @Given("it exports package {string} using {string}")
    public void itExportsPackageUsing(String pkg, String uses) {
        world.export(pkg, null, uses);
    }

    /**
     * The step {@code it exports package {string} in version {string} using {string}}.
     *
     * @param pkg the package name
     * @param version the version
     * @param uses the packages of the uses directive
     */
    @Given("it exports package {string} in version {string} using {string}")
    public void itExportsPackageInVersionUsing(String pkg, String version, String uses) {
        world.export(pkg, version, uses);
    }

    /**
     * The step {@code it imports package {string}}.
     *
     * @param pkg the package name
     */
    @Given("it imports package {string}")
    public void itImportsPackage(String pkg) {
        world.importPackage(pkg, null, false);
    }

    /**
     * The step {@code it optionally imports package {string}}.
     *
     * @param pkg the package name
     */
    @Given("it optionally imports package {string}")
    public void itOptionallyImportsPackage(String pkg) {
        world.importPackage(pkg, null, true);
    }

    /**
     * The step {@code it imports package {string} in version range {string}}.
     *
     * @param pkg the package name
     * @param range the version range
     */
    @Given("it imports package {string} in version range {string}")
    public void itImportsPackageInVersionRange(String pkg, String range) {
        world.importPackage(pkg, range, false);
    }

    /**
     * The step {@code it optionally imports package {string} in version range {string}}.
     *
     * @param pkg the package name
     * @param range the version range
     */
    @Given("it optionally imports package {string} in version range {string}")
    public void itOptionallyImportsPackageInVersionRange(String pkg, String range) {
        world.importPackage(pkg, range, true);
    }

    /**
     * The step {@code there is {bundle} with fragment attachment policy {string}}.
     *
     * @param ref the bundle
     * @param policy the fragment attachment policy
     */
    @Given("there is {bundle} with fragment attachment policy {string}")
    public void thereIsWithFragmentAttachmentPolicy(BundleRef ref, String policy) {
        world.unsupported("fragment attachment policy");
    }

    /**
     * The step {@code there is a fragment {bundle} of bundle {string}}.
     *
     * @param ref the bundle
     * @param host the symbolic name of the host
     */
    @Given("there is a fragment {bundle} of bundle {string}")
    public void thereIsAFragmentOfBundle(BundleRef ref, String host) {
        world.unsupported("fragment bundles");
    }

    /**
     * The step {@code there is a fragment {bundle} of bundle {string} in version range {string}}.
     *
     * @param ref the bundle
     * @param host the symbolic name of the host
     * @param range the version range
     */
    @Given("there is a fragment {bundle} of bundle {string} in version range {string}")
    public void thereIsAFragmentOfBundleInVersionRange(BundleRef ref, String host, String range) {
        world.unsupported("fragment bundles");
    }

    /**
     * The step {@code it exports package {string} with {string}}.
     *
     * @param pkg the package name
     * @param attributes the attributes and directives as written in a manifest clause
     */
    @Given("it exports package {string} with {string}")
    public void itExportsPackageWith(String pkg, String attributes) {
        world.unsupported("export attributes and directives");
    }

    /**
     * The step {@code it exports package {string} in version {string} with {string}}.
     *
     * @param pkg the package name
     * @param version the version
     * @param attributes the attributes and directives as written in a manifest clause
     */
    @Given("it exports package {string} in version {string} with {string}")
    public void itExportsPackageInVersionWith(String pkg, String version, String attributes) {
        world.unsupported("export attributes and directives");
    }

    /**
     * The step {@code it imports package {string} with {string}}.
     *
     * @param pkg the package name
     * @param attributes the attributes and directives as written in a manifest clause
     */
    @Given("it imports package {string} with {string}")
    public void itImportsPackageWith(String pkg, String attributes) {
        world.unsupported("import attributes and directives");
    }

    /**
     * The step {@code it imports package {string} with multiple cardinality}.
     *
     * @param pkg the package name
     */
    @Given("it imports package {string} with multiple cardinality")
    public void itImportsPackageWithMultipleCardinality(String pkg) {
        world.unsupported("multiple cardinality");
    }

    /**
     * The step {@code it imports package {string} from bundle {string}}.
     *
     * @param pkg the package name
     * @param bundle the symbolic name of the bundle
     */
    @Given("it imports package {string} from bundle {string}")
    public void itImportsPackageFromBundle(String pkg, String bundle) {
        world.unsupported("import from a specific bundle");
    }

    /**
     * The step {@code it imports package {string} from bundle {string} in bundle version range {string}}.
     *
     * @param pkg the package name
     * @param bundle the symbolic name of the bundle
     * @param range the version range
     */
    @Given("it imports package {string} from bundle {string} in bundle version range {string}")
    public void itImportsPackageFromBundleInBundleVersionRange(String pkg, String bundle, String range) {
        world.unsupported("import from a specific bundle");
    }

    /**
     * The step {@code it dynamically imports package {string}}.
     *
     * @param pkg the package name
     */
    @Given("it dynamically imports package {string}")
    public void itDynamicallyImportsPackage(String pkg) {
        world.unsupported("dynamic imports");
    }

    /**
     * The step {@code it requires bundle {string}}.
     *
     * @param bundle the symbolic name of the bundle
     */
    @Given("it requires bundle {string}")
    public void itRequiresBundle(String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it requires bundle {string} in version range {string}}.
     *
     * @param bundle the symbolic name of the bundle
     * @param range the version range
     */
    @Given("it requires bundle {string} in version range {string}")
    public void itRequiresBundleInVersionRange(String bundle, String range) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it requires bundle {string} with multiple cardinality}.
     *
     * @param bundle the symbolic name of the bundle
     */
    @Given("it requires bundle {string} with multiple cardinality")
    public void itRequiresBundleWithMultipleCardinality(String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it requires bundle {string} and reexports it}.
     *
     * @param bundle the symbolic name of the bundle
     */
    @Given("it requires bundle {string} and reexports it")
    public void itRequiresBundleAndReexportsIt(String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it optionally requires bundle {string} and reexports it}.
     *
     * @param bundle the symbolic name of the bundle
     */
    @Given("it optionally requires bundle {string} and reexports it")
    public void itOptionallyRequiresBundleAndReexportsIt(String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it optionally requires bundle {string}}.
     *
     * @param bundle the symbolic name of the bundle
     */
    @Given("it optionally requires bundle {string}")
    public void itOptionallyRequiresBundle(String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code it provides capability {string} with {string}}.
     *
     * @param namespace the capability namespace
     * @param attributes the attributes and directives as written in a manifest clause
     */
    @Given("it provides capability {string} with {string}")
    public void itProvidesCapabilityWith(String namespace, String attributes) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it provides capability {string}}.
     *
     * @param namespace the capability namespace
     */
    @Given("it provides capability {string}")
    public void itProvidesCapability(String namespace) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it requires capability {string}}.
     *
     * @param namespace the capability namespace
     */
    @Given("it requires capability {string}")
    public void itRequiresCapability(String namespace) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it requires capability {string} with {string}}.
     *
     * @param namespace the capability namespace
     * @param attributes the attributes and directives as written in a manifest clause
     */
    @Given("it requires capability {string} with {string}")
    public void itRequiresCapabilityWith(String namespace, String attributes) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it requires capability {string} with filter {string}}.
     *
     * @param namespace the capability namespace
     * @param filter the filter
     */
    @Given("it requires capability {string} with filter {string}")
    public void itRequiresCapabilityWithFilter(String namespace, String filter) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it requires capability {string} with filter {string} and {string}}.
     *
     * @param namespace the capability namespace
     * @param filter the filter
     * @param directives the additional directives
     */
    @Given("it requires capability {string} with filter {string} and {string}")
    public void itRequiresCapabilityWithFilterAnd(String namespace, String filter, String directives) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code it optionally requires capability {string} with filter {string}}.
     *
     * @param namespace the capability namespace
     * @param filter the filter
     */
    @Given("it optionally requires capability {string} with filter {string}")
    public void itOptionallyRequiresCapabilityWithFilter(String namespace, String filter) {
        world.unsupported("generic capabilities");
    }
}
