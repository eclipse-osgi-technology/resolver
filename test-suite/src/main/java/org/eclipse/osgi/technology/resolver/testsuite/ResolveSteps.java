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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.verifier.ClassSpace;
import org.eclipse.osgi.technology.resolver.verifier.ConsistencyVerifier;
import org.eclipse.osgi.technology.resolver.verifier.WiringVerifier;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;

import aQute.bnd.osgi.resource.ResourceUtils;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Cucumber glue steps to perform a resolve and to inspect the outcome. What the
 * OSGi API defines is checked directly on the returned wires. What a resolver
 * reports about a failure is not defined by the specification, so such
 * assertions are delegated to the {@link ResolverEngine} of the world.
 */
public class ResolveSteps {

    private final TestWorld world;
    private Map<Resource, List<Wire>> result;
    private ResolutionException failure;
    private boolean resolved;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public ResolveSteps(TestWorld world) {
        this.world = world;
    }

    /**
     * Every successful result of every scenario must be a well formed wiring
     * and have consistent class spaces, whatever the scenario asserts. The
     * wiring is checked first as the consistency check assumes it.
     */
    @After
    public void resultIsWellFormedAndConsistent() {
        if (resolved && failure == null) {
            assertThat(new WiringVerifier(world.mandatoryResources(), world.optionalResources(), result)
                    .violations()).as("malformed wiring").isEmpty();
            assertThat(new ConsistencyVerifier(result).violations()).as("class space inconsistencies")
                    .isEmpty();
        }
    }

    /**
     * Resolves one bundle.
     *
     * @param ref the bundle that has to be resolved
     */
    @When("I resolve {bundle}")
    public void iResolve(BundleRef ref) {
        resolve(List.of(ref));
    }

    /**
     * Resolves two bundles.
     *
     * @param a the first bundle that has to be resolved
     * @param b the second bundle that has to be resolved
     */
    @When("I resolve {bundle} and {bundle}")
    public void iResolveTwo(BundleRef a, BundleRef b) {
        resolve(List.of(a, b));
    }

    /**
     * Resolves one bundle that has to be resolved and one that is resolved if
     * possible.
     *
     * @param mandatory the bundle that has to be resolved
     * @param optional  the bundle that is resolved if possible
     */
    @When("I resolve {bundle} and optionally {bundle}")
    public void iResolveOptionally(BundleRef mandatory, BundleRef optional) {
        resolve(List.of(mandatory), List.of(optional));
    }

    /** Resolves without any bundle that has to be resolved. */
    @When("I resolve nothing")
    public void iResolveNothing() {
        resolve(List.of(), List.of());
    }

    private void resolve(List<BundleRef> mandatory) {
        resolve(mandatory, List.of());
    }

    private void resolve(List<BundleRef> mandatory, List<BundleRef> optional) {
        resolved = true;
        try {
            result = world.resolve(mandatory, optional);
        } catch (ResolutionException e) {
            failure = e;
        }
    }

    /** Asserts that the resolve succeeded without wiring any bundle. */
    @Then("the resulting wiring is empty")
    public void theResultingWiringIsEmpty() {
        theResolutionSucceeds();
        assertThat(result).isEmpty();
    }

    /** Asserts that the resolve succeeded. */
    @Then("the resolution succeeds")
    public void theResolutionSucceeds() {
        assertThat(failure).as("resolution failure").isNull();
        assertThat(result).as("resolution result").isNotNull();
    }

    /**
     * Asserts that the resolve failed because a bundle has an import that no
     * export satisfies.
     *
     * @param ref         the bundle with the unsatisfied import
     * @param packageName the name of the package
     */
    @Then("the resolution fails because {bundle} has an unsatisfied import of package {string}")
    public void failsBecauseOfUnsatisfiedImport(BundleRef ref, String packageName) {
        unsatisfied(ref, PackageNamespace.PACKAGE_NAMESPACE, packageName);
    }

    /**
     * Asserts that the resolve failed because a bundle requires a bundle that
     * is not available.
     *
     * @param ref  the bundle with the unsatisfied requirement
     * @param name the symbolic name of the required bundle
     */
    @Then("the resolution fails because {bundle} has an unsatisfied requirement of bundle {string}")
    public void failsBecauseOfUnsatisfiedRequireBundle(BundleRef ref, String name) {
        unsatisfied(ref, BundleNamespace.BUNDLE_NAMESPACE, name);
    }

    /**
     * Asserts that the resolve failed because a bundle has a requirement in a
     * namespace that no capability satisfies.
     *
     * @param ref       the bundle with the unsatisfied requirement
     * @param namespace the namespace of the requirement
     */
    @Then("the resolution fails because {bundle} has an unsatisfied requirement for capability in namespace {string}")
    public void failsBecauseOfUnsatisfiedCapability(BundleRef ref, String namespace) {
        unsatisfied(ref, namespace, null);
    }

    private void unsatisfied(BundleRef ref, String namespace, String name) {
        assertThat(failure).as("resolution failure, but resolved: " + result).isNotNull();
        world.engine().assertUnsatisfiedRequirement(failure, world.find(ref), namespace, name);
    }

    /**
     * Asserts that a bundle is part of the result.
     *
     * @param ref the bundle
     */
    @Then("{bundle} is resolved")
    public void isResolved(BundleRef ref) {
        theResolutionSucceeds();
        assertThat(result).containsKey(world.find(ref));
    }

    /**
     * Asserts that a bundle is not part of the result of a successful resolve.
     *
     * @param ref the bundle
     */
    @Then("{bundle} is not resolved")
    public void isNotResolved(BundleRef ref) {
        // a failed resolve must not satisfy this, it has its own assertion
        theResolutionSucceeds();
        assertThat(result).doesNotContainKey(world.find(ref));
    }

    /**
     * Asserts which bundles are part of the result, no more and no fewer.
     *
     * @param names the symbolic names separated by comma, an entry like
     *              {@code name 1.0.0} tells apart bundles of the same name
     */
    @Then("the resolved bundles are exactly {string}")
    public void theResolvedBundlesAreExactly(String names) {
        theResolutionSucceeds();
        List<String> expected = names.isEmpty() ? List.of() : List.of(names.split("\\s*,\\s*"));
        List<String> actual = result.keySet().stream().map(r -> {
            String versioned = ResourceUtils.getIdentity(r) + " " + ResourceUtils.getVersion(r);
            return expected.contains(versioned) ? versioned : ResourceUtils.getIdentity(r);
        }).toList();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Asserts that a bundle is wired to an export of a package of another
     * bundle.
     *
     * @param requirer    the importing bundle
     * @param provider    the exporting bundle
     * @param packageName the name of the package
     */
    @Then("{bundle} is wired to {bundle} for package {string}")
    public void isWiredToForPackage(BundleRef requirer, BundleRef provider, String packageName) {
        assertThat(wires(requirer)).as("wires of %s", requirer)
                .anyMatch(w -> w.getProvider().equals(world.find(provider))
                        && PackageNamespace.PACKAGE_NAMESPACE.equals(w.getCapability().getNamespace())
                        && packageName.equals(w.getCapability().getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE)));
    }

    /**
     * Asserts that a bundle is wired to another bundle, whatever the wire is
     * for.
     *
     * @param requirer the requiring bundle
     * @param provider the providing bundle
     */
    @Then("{bundle} is wired to {bundle}")
    public void isWiredTo(BundleRef requirer, BundleRef provider) {
        assertThat(wires(requirer)).as("wires of %s", requirer)
                .anyMatch(w -> w.getProvider().equals(world.find(provider)));
    }

    /**
     * Asserts that a bundle is wired to a capability of another bundle in a
     * namespace.
     *
     * @param requirer  the requiring bundle
     * @param provider  the providing bundle
     * @param namespace the namespace of the capability
     */
    @Then("{bundle} is wired to {bundle} for capability in namespace {string}")
    public void isWiredToForNamespace(BundleRef requirer, BundleRef provider, String namespace) {
        assertThat(wires(requirer)).as("wires of %s", requirer)
                .anyMatch(w -> w.getProvider().equals(world.find(provider))
                        && namespace.equals(w.getCapability().getNamespace()));
    }

    /**
     * Asserts that a bundle has no wire to another bundle.
     *
     * @param requirer the requiring bundle
     * @param provider the bundle that must not provide anything to it
     */
    @Then("{bundle} is not wired to {bundle}")
    public void isNotWiredTo(BundleRef requirer, BundleRef provider) {
        assertThat(wires(requirer)).as("wires of %s", requirer)
                .noneMatch(w -> w.getProvider().equals(world.find(provider)));
    }

    /**
     * Asserts the number of wires of a bundle.
     *
     * @param ref   the bundle
     * @param count the expected number of wires
     */
    @Then("{bundle} has {int} wire(s)")
    public void hasWires(BundleRef ref, int count) {
        assertThat(wires(ref)).as("wires of %s", ref).hasSize(count);
    }

    /**
     * Asserts that a bundle sees a package, through an import or a required
     * bundle or its own export, from exactly one bundle.
     *
     * @param ref         the bundle that sees the package
     * @param packageName the name of the package
     * @param provider    the only bundle the package comes from
     */
    @Then("{bundle} sees package {string} from {bundle}")
    public void seesPackageFrom(BundleRef ref, String packageName, BundleRef provider) {
        wires(ref);
        assertThat(new ClassSpace(result).sources(world.find(ref), packageName))
                .as("providers of %s visible to %s", packageName, ref).containsExactly(world.find(provider));
    }

    private List<Wire> wires(BundleRef ref) {
        theResolutionSucceeds();
        List<Wire> wires = result.get(world.find(ref));
        assertThat(wires).as("%s is resolved", ref).isNotNull();
        return wires;
    }
}
