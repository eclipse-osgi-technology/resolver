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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.osgi.technology.resolver.testsuite.BundleRef;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * The steps that resolve and assert. They only record the expectation, the
 * Alloy model is written and solved once in {@link #solve(Scenario)} after the
 * scenario, so the step wording stays the one of the other bindings.
 */
public class ResolveSteps {

    private final AlloyWorld world;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public ResolveSteps(AlloyWorld world) {
        this.world = world;
    }

    /**
     * Asks Alloy whether the expectation of the scenario holds: for a
     * successful resolve there has to be a valid resolution that satisfies the
     * assertions, for a failed one there must not be any.
     *
     * @param scenario the running scenario, names the generated model
     */
    @After
    public void solve(Scenario scenario) {
        if (world.skipped || world.mandatory == null) {
            return;
        }
        Outcome outcome = world.outcome == null ? Outcome.SUCCEEDS : world.outcome;
        String name = scenario.getUri().toString().replaceAll(".*[/:]", "").replace(".feature", "") + "-"
                + scenario.getLine();
        AlloyRunner runner = new AlloyRunner(name, AlloyModelWriter.write(world, scenario.getName()));
        switch (outcome) {
        case SUCCEEDS -> {
            if (!runner.satisfiable("satisfiable")) {
                throw new AssertionError(runner.satisfiable("validResolution")
                        ? "A valid resolution exists, but none satisfies: " + world.descriptions
                        : "There is no valid resolution");
            }
        }
        case FAILS -> assertThat(runner.satisfiable("validResolution")).as("a valid resolution exists").isFalse();
        case FAILS_BECAUSE_OF_USES -> {
            assertThat(runner.satisfiable("validResolution")).as("a valid resolution exists").isFalse();
            assertThat(runner.satisfiable("withoutUses")).as("the resolve also fails without the uses constraint")
                    .isTrue();
        }
        }
    }

    private void resolve(BundleRef... refs) {
        world.mandatory = java.util.Arrays.stream(refs).map(world::find).toList();
    }

    /**
     * The step {@code I resolve {bundle}}.
     *
     * @param ref the bundle
     */
    @When("I resolve {bundle}")
    public void iResolve(BundleRef ref) {
        resolve(ref);
    }

    /**
     * The step {@code I resolve {bundle} and {bundle}}.
     *
     * @param a the first bundle
     * @param b the second bundle
     */
    @When("I resolve {bundle} and {bundle}")
    public void iResolveTwo(BundleRef a, BundleRef b) {
        resolve(a, b);
    }

    /**
     * The step {@code I resolve all bundles}.
     */
    @When("I resolve all bundles")
    public void iResolveAll() {
        world.mandatory = List.copyOf(world.bundles);
    }

    /**
     * The step {@code I resolve nothing}.
     */
    @When("I resolve nothing")
    public void iResolveNothing() {
        world.mandatory = List.of();
    }

    /**
     * The step {@code I resolve {bundle} and optionally {bundle}}.
     *
     * @param mandatory the bundle that has to be resolved
     * @param optional the optional bundle
     */
    @When("I resolve {bundle} and optionally {bundle}")
    public void iResolveOptionally(BundleRef mandatory, BundleRef optional) {
        world.unsupported("optional resources");
    }

    /**
     * The step {@code I resolve {bundle} and optionally the bundles {string}}.
     *
     * @param mandatory the bundle that has to be resolved
     * @param optionals the symbolic names of the optional bundles, separated by comma
     */
    @When("I resolve {bundle} and optionally the bundles {string}")
    public void iResolveOptionallyAll(BundleRef mandatory, String optionals) {
        world.unsupported("optional resources");
    }

    /**
     * The step {@code I resolve all bundles as optional resources}.
     */
    @When("I resolve all bundles as optional resources")
    public void iResolveAllOptionally() {
        world.unsupported("optional resources");
    }

    /**
     * The step {@code the resulting wiring is empty}.
     */
    @Then("the resulting wiring is empty")
    public void theResultingWiringIsEmpty() {
        world.unsupported("empty wiring of a resolve without mandatory resources");
    }

    /**
     * The step {@code the resolution succeeds}.
     */
    @Then("the resolution succeeds")
    public void theResolutionSucceeds() {
        world.outcome = Outcome.SUCCEEDS;
    }

    /**
     * The step {@code the resolution fails because {bundle} has an unsatisfied import of package {string}}.
     *
     * @param ref the bundle
     * @param pkg the package name
     */
    @Then("the resolution fails because {bundle} has an unsatisfied import of package {string}")
    public void unsatisfiedImport(BundleRef ref, String pkg) {
        world.outcome = Outcome.FAILS;
    }

    /**
     * The step {@code the resolution fails because {bundle} has an unsatisfied requirement of bundle {string}}.
     *
     * @param ref the bundle
     * @param bundle the symbolic name of the bundle
     */
    @Then("the resolution fails because {bundle} has an unsatisfied requirement of bundle {string}")
    public void unsatisfiedRequireBundle(BundleRef ref, String bundle) {
        world.unsupported("Require-Bundle");
    }

    /**
     * The step {@code the resolution fails because {bundle} has an unsatisfied requirement for capability in namespace {string}}.
     *
     * @param ref the bundle
     * @param namespace the capability namespace
     */
    @Then("the resolution fails because {bundle} has an unsatisfied requirement for capability in namespace {string}")
    public void unsatisfiedCapability(BundleRef ref, String namespace) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code the resolution fails because of a uses constraint violation on package {string} for {bundle}}.
     *
     * @param pkg the package name
     * @param ref the bundle
     */
    @Then("the resolution fails because of a uses constraint violation on package {string} for {bundle}")
    public void usesViolation(String pkg, BundleRef ref) {
        world.outcome = Outcome.FAILS_BECAUSE_OF_USES;
    }

    /**
     * The step {@code the resolution fails because the import of package {string} of {bundle} is hidden by a substituted export}.
     *
     * @param pkg the package name
     * @param ref the bundle
     */
    @Then("the resolution fails because the import of package {string} of {bundle} is hidden by a substituted export")
    public void hiddenBySubstitution(String pkg, BundleRef ref) {
        world.outcome = Outcome.FAILS;
    }

    /**
     * The step {@code the resolution fails because {bundle} has no host {string}}.
     *
     * @param ref the bundle
     * @param host the symbolic name of the host
     */
    @Then("the resolution fails because {bundle} has no host {string}")
    public void noHost(BundleRef ref, String host) {
        world.unsupported("fragment bundles");
    }

    /**
     * The step {@code {bundle} is resolved}.
     *
     * @param ref the bundle
     */
    @Then("{bundle} is resolved")
    public void isResolved(BundleRef ref) {
        world.assertThat(ref + " is resolved", world.find(ref).atom + " in r.resolved");
    }

    /**
     * The step {@code {bundle} is not resolved}.
     *
     * @param ref the bundle
     */
    @Then("{bundle} is not resolved")
    public void isNotResolved(BundleRef ref) {
        world.assertThat(ref + " is not resolved", world.find(ref).atom + " not in r.resolved");
    }

    /**
     * The step {@code the resolved bundles are exactly {string}}.
     *
     * @param names the symbolic names of the bundles, separated by comma
     */
    @Then("the resolved bundles are exactly {string}")
    public void resolvedExactly(String names) {
        List<String> atoms = java.util.Arrays.stream(names.split("\\s*,\\s*"))
                .map(n -> world.find(BundleRef.of(n)).atom).toList();
        world.assertThat("the resolved bundles are exactly " + names, "r.resolved = " + String.join(" + ", atoms));
    }

    /**
     * The step {@code {bundle} is wired to {bundle} for package {string}}.
     *
     * @param from the bundle that is wired
     * @param to the bundle that provides
     * @param pkg the package name
     */
    @Then("{bundle} is wired to {bundle} for package {string}")
    public void wiredForPackage(BundleRef from, BundleRef to, String pkg) {
        world.assertThat(from + " is wired to " + to + " for package " + pkg,
                "some i : " + world.find(from).atom + ".imports | i.pkg = " + AlloyWorld.pkg(pkg)
                        + " and some i.(r.wire) and i.(r.wire) in " + world.find(to).atom + ".exports");
    }

    /**
     * The step {@code {bundle} is wired to {bundle}}.
     *
     * @param from the bundle that is wired
     * @param to the bundle that provides
     */
    @Then("{bundle} is wired to {bundle}")
    public void wired(BundleRef from, BundleRef to) {
        world.assertThat(from + " is wired to " + to, wiredFormula(from, to));
    }

    /**
     * The step {@code {bundle} is not wired to {bundle}}.
     *
     * @param from the bundle that is wired
     * @param to the bundle that provides
     */
    @Then("{bundle} is not wired to {bundle}")
    public void notWired(BundleRef from, BundleRef to) {
        world.assertThat(from + " is not wired to " + to, "not (" + wiredFormula(from, to) + ")");
    }

    private String wiredFormula(BundleRef from, BundleRef to) {
        String f = world.find(from).atom;
        String t = world.find(to).atom;
        return "some i : " + f + ".imports | some i.(r.wire) and i.(r.wire) in " + t + ".exports and " + f + " != "
                + t;
    }

    /**
     * The step {@code {bundle} is wired to {bundle} for capability in namespace {string}}.
     *
     * @param from the bundle that is wired
     * @param to the bundle that provides
     * @param namespace the capability namespace
     */
    @Then("{bundle} is wired to {bundle} for capability in namespace {string}")
    public void wiredForCapability(BundleRef from, BundleRef to, String namespace) {
        world.unsupported("generic capabilities");
    }

    /**
     * The step {@code {bundle} has {int} wire(s)}.
     *
     * @param ref the bundle
     * @param count the number of wires
     */
    @Then("{bundle} has {int} wire(s)")
    public void hasWires(BundleRef ref, int count) {
        String b = world.find(ref).atom;
        world.assertThat(ref + " has " + count + " wires",
                "#{ i : " + b + ".imports | some e : i.(r.wire) | e not in " + b + ".exports } = " + count);
    }

    /**
     * The step {@code {bundle} sees package {string} from {bundle}}.
     *
     * @param viewer the bundle that sees the package
     * @param pkg the package name
     * @param provider the bundle that provides the package
     */
    @Then("{bundle} sees package {string} from {bundle}")
    public void seesPackage(BundleRef viewer, String pkg, BundleRef provider) {
        String seen = AlloyWorld.pkg(pkg) + ".(view[r, " + world.find(viewer).atom + "])";
        world.assertThat(viewer + " sees package " + pkg + " from " + provider,
                "some " + seen + " and " + seen + " in " + world.find(provider).atom + ".exports");
    }

    /**
     * The step {@code {bundle} substitutes its export of package {string}}.
     *
     * @param ref the bundle
     * @param pkg the package name
     */
    @Then("{bundle} substitutes its export of package {string}")
    public void substitutes(BundleRef ref, String pkg) {
        exportState(ref, pkg, "in", "substitutes");
    }

    /**
     * The step {@code {bundle} keeps its export of package {string}}.
     *
     * @param ref the bundle
     * @param pkg the package name
     */
    @Then("{bundle} keeps its export of package {string}")
    public void keeps(BundleRef ref, String pkg) {
        exportState(ref, pkg, "not in", "keeps");
    }

    private void exportState(BundleRef ref, String pkg, String operator, String verb) {
        String b = world.find(ref).atom;
        world.assertThat(ref + " " + verb + " its export of package " + pkg, b + " in r.resolved and some e : " + b
                + ".exports | e.pkg = " + AlloyWorld.pkg(pkg) + " and e " + operator + " r.substituted");
    }
}
