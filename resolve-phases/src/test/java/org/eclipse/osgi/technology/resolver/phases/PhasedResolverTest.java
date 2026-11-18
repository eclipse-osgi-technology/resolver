/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.phases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.osgi.technology.resolver.phases.Bundles.bundle;
import static org.eclipse.osgi.technology.resolver.phases.Bundles.export;
import static org.eclipse.osgi.technology.resolver.phases.Bundles.fragment;
import static org.eclipse.osgi.technology.resolver.phases.Bundles.importing;
import static org.eclipse.osgi.technology.resolver.phases.Bundles.optional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.failures.FragmentConflictException;
import org.eclipse.osgi.technology.resolver.failures.UnsatisfiedRequirementException;
import org.eclipse.osgi.technology.resolver.failures.UsesConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * A resolver that runs the phases, seen from the outside: resources in, a
 * resolve call, wires or an exception and the published diagnostics out.
 */
class PhasedResolverTest {

    private final FailingStrategy strategy = new FailingStrategy();
    private final PhasedResolver resolver = new PhasedResolver() {
        @Override
        protected UsesConflictStrategy createStrategy(ResolveContext context) {
            return strategy;
        }
    };

    private static Resource api(String version) {
        ResourceBuilder builder = bundle("api-" + version);
        builder.addExportPackage("api", export(version));
        return builder.build();
    }

    private static Resource consumer(String name, String range) {
        ResourceBuilder builder = bundle(name);
        builder.addImportPackage("api", importing(range));
        return builder.build();
    }

    @Test
    void anImportIsWiredToTheHighestVersionOfTheExport() throws ResolutionException {
        Resource consumer = consumer("consumer", "[1,3)");
        Resource v1 = api("1.0.0");
        Resource v2 = api("2.0.0");

        Map<Resource, List<Wire>> wires = resolver.resolve(TestContext.resolving(consumer).offering(v1, v2));

        assertThat(wires.get(consumer)).singleElement().satisfies(wire -> assertThat(wire.getProvider()).isSameAs(v2));
        assertThat(wires).doesNotContainKey(v1);
    }

    @Test
    void aSubclassCanReplaceAPhase() throws ResolutionException {
        Resource consumer = consumer("consumer", "[1,3)");
        PhasedResolver silent = new PhasedResolver() {
            @Override
            protected UsesConflictStrategy createStrategy(ResolveContext context) {
                return strategy;
            }

            @Override
            protected ResolverPhase<Map<Resource, List<Wire>>> createWiringPhase(ResolverGraph graph) {
                return Collections::emptyMap;
            }
        };

        assertThat(silent.resolve(TestContext.resolving(consumer).offering(api("1.0.0")))).isEmpty();
    }

    @Test
    void aResourceOnlyAvailableIsNotPartOfTheResult() throws ResolutionException {
        Resource consumer = consumer("consumer", "[1,3)");
        Resource unrelated = consumer("unrelated", "[1,3)");

        Resource api = api("1.0.0");

        Map<Resource, List<Wire>> wires = resolver.resolve(TestContext.resolving(consumer).offering(api, unrelated));

        assertThat(wires).containsOnlyKeys(consumer, api);
    }

    @Test
    void aMandatoryImportWithoutProviderFailsTheResolveAndNamesTheRequirement() {
        Resource consumer = consumer("consumer", "[1,3)");

        assertThatThrownBy(() -> resolver.resolve(TestContext.resolving(consumer).offering(api("3.0.0"))))
                .isInstanceOfSatisfying(UnsatisfiedRequirementException.class, failure -> assertThat(
                        failure.getRequirement().getResource()).isSameAs(consumer));
    }

    @Test
    void anOptionalImportWithoutProviderIsLeftUnwired() throws ResolutionException {
        ResourceBuilder builder = bundle("consumer");
        builder.addImportPackage("api", optional("[1,3)"));
        Resource consumer = builder.build();

        Map<Resource, List<Wire>> wires = resolver.resolve(TestContext.resolving(consumer));

        assertThat(wires.get(consumer)).isEmpty();
    }

    @Test
    void anOptionalResourceThatCanNotBeResolvedDoesNotFailTheResolve() throws ResolutionException {
        Resource consumer = consumer("consumer", "[1,3)");
        Resource broken = consumer("broken", "[5,6)");

        Map<Resource, List<Wire>> wires = resolver
                .resolve(TestContext.resolving(consumer).offering(api("1.0.0")).optionally(broken));

        assertThat(wires).containsKey(consumer).doesNotContainKey(broken);
    }

    @Test
    void aFragmentIsWiredToItsHostAndItsImportsAreWiredForTheHost() throws ResolutionException {
        Resource host = bundle("host").build();
        ResourceBuilder fragmentBuilder = fragment("frag", "1.0.0", "host");
        fragmentBuilder.addImportPackage("api", importing("[1,2)"));
        Resource fragment = fragmentBuilder.build();
        Resource api = api("1.0.0");

        Map<Resource, List<Wire>> wires = resolver.resolve(TestContext.resolving(host, fragment).offering(api));

        assertThat(wires.get(fragment)).extracting(Wire::getProvider).contains(host);
        assertThat(wires.get(host)).extracting(Wire::getProvider).contains(api);
    }

    @Test
    void onlyTheHighestVersionOfAFragmentIsAttachedToAHost() throws ResolutionException {
        Resource host = bundle("host").build();
        ResourceBuilder low = fragment("frag", "1.0.0", "host");
        low.addImportPackage("api", importing("[1,2)"));
        ResourceBuilder high = fragment("frag", "2.0.0", "host");
        high.addImportPackage("api", importing("[1,2)"));
        Resource lowFragment = low.build();
        Resource highFragment = high.build();

        Map<Resource, List<Wire>> wires = resolver
                .resolve(TestContext.resolving(host).offering(api("1.0.0")).optionally(lowFragment, highFragment));

        assertThat(wires).containsKey(highFragment).doesNotContainKey(lowFragment);
    }

    @Test
    void theStrategyDecidesTheConflictTheRulesLeaveOpen() {
        ResourceBuilder exporter = bundle("exporter");
        exporter.addExportPackage("p", export("1.0.0", "api"));
        exporter.addImportPackage("api", importing("[1,2)"));
        ResourceBuilder client = bundle("client");
        client.addImportPackage("p", importing("[1,2)"));
        client.addImportPackage("api", importing("[2,3)"));
        Resource clientResource = client.build();

        assertThatThrownBy(() -> resolver.resolve(TestContext.resolving(clientResource)
                .offering(exporter.build(), api("1.0.0"), api("2.0.0"))))
                        .isInstanceOfSatisfying(UsesConstraintViolationException.class, failure -> {
                            assertThat(failure.getPackageName()).isEqualTo("api");
                            assertThat(failure.getResource()).isSameAs(clientResource);
                            assertThat(failure.getUnresolvedRequirements())
                                    .extracting(requirement -> requirement.getResource())
                                    .contains(clientResource);
                        });
        assertThat(strategy.calls).isEqualTo(1);
    }

    @Test
    void diagnosticsArePublishedOnceForASuccessfulResolve() throws ResolutionException {
        Resource consumer = consumer("consumer", "[1,3)");
        TestContext context = TestContext.resolving(consumer).offering(api("1.0.0"));

        resolver.resolve(context);

        assertThat(context.published).singleElement()
                .satisfies(diagnostics -> assertThat(diagnostics.getMessage(consumer)).isPresent());
    }

    @Test
    void diagnosticsArePublishedOnceForAFailedResolveAndTellWhatIsMissing() {
        Resource consumer = consumer("consumer", "[1,3)");
        TestContext context = TestContext.resolving(consumer);

        assertThatThrownBy(() -> resolver.resolve(context)).isInstanceOf(ResolutionException.class);

        assertThat(context.published).singleElement().satisfies(diagnostics -> assertThat(
                diagnostics.getMessage(consumer)).hasValueSatisfying(message -> assertThat(message).contains("api")));
    }

    private static Resource hostImporting(String range) {
        ResourceBuilder builder = bundle("host");
        builder.addImportPackage("api", importing(range));
        return builder.build();
    }

    private static Resource fragmentImporting(String name, String range) {
        ResourceBuilder builder = fragment(name, "1.0.0", "host");
        builder.addImportPackage("api", importing(range));
        return builder.build();
    }

    @Test
    void aFragmentThatMakesItsHostInconsistentIsNotAttachedToIt() throws ResolutionException {
        Resource host = hostImporting("[2,3)");
        Resource fragment = fragmentImporting("frag", "[1,2)");
        Resource api2 = api("2.0.0");
        TestContext context = TestContext.resolving(host).offering(api("1.0.0"), api2).optionally(fragment);

        Map<Resource, List<Wire>> wires = resolver.resolve(context);

        assertThat(wires.get(host)).singleElement().satisfies(wire -> assertThat(wire.getProvider()).isSameAs(api2));
        assertThat(wires).doesNotContainKey(fragment);
        assertThat(strategy.calls).isEqualTo(2);
        assertThat(context.published).singleElement().satisfies(diagnostics -> assertThat(
                diagnostics.getMessage(fragment)).hasValueSatisfying(message -> assertThat(message).contains("Not attached to the inconsistent host")));
    }

    @Test
    void everyFragmentThatTakesPartInTheConflictIsLeftOutInOneRound() throws ResolutionException {
        Resource host = hostImporting("[2,3)");
        Resource first = fragmentImporting("first", "[1,2)");
        Resource second = fragmentImporting("second", "[1,2)");

        Map<Resource, List<Wire>> wires = resolver
                .resolve(TestContext.resolving(host).offering(api("1.0.0"), api("2.0.0")).optionally(first, second));

        assertThat(wires).containsKey(host).doesNotContainKeys(first, second);
        assertThat(strategy.calls).isEqualTo(2);
    }

    @Test
    void aHostedCapabilityThatMakesAnotherResourceInconsistentIsNotProvided() throws ResolutionException {
        Resource host = bundle("host").build();
        ResourceBuilder fragmentBuilder = fragment("frag", "1.0.0", "host");
        fragmentBuilder.addExportPackage("p", export("1.0.0", "api"));
        fragmentBuilder.addImportPackage("api", importing("[2,3)"));
        Resource fragment = fragmentBuilder.build();
        ResourceBuilder clientBuilder = bundle("client");
        clientBuilder.addImportPackage("p", optional("[1,2)"));
        clientBuilder.addImportPackage("api", importing("[1,2)"));
        Resource client = clientBuilder.build();
        Resource api1 = api("1.0.0");

        Map<Resource, List<Wire>> wires = resolver.resolve(
                TestContext.resolving(client).offering(host, fragment, api1, api("2.0.0")));

        assertThat(wires.get(client)).extracting(Wire::getProvider).containsExactly(api1);
        assertThat(wires).doesNotContainKey(fragment);
        assertThat(strategy.calls).isEqualTo(2);
    }

    @Test
    void aResourceThatNeedsAFragmentThatWasLeftOutFailsWithTheConflictOfTheHost() {
        Resource host = hostImporting("[2,3)");
        ResourceBuilder fragmentBuilder = fragment("frag", "1.0.0", "host");
        fragmentBuilder.addImportPackage("api", importing("[1,2)"));
        fragmentBuilder.addExportPackage("foo", export("1.0.0"));
        ResourceBuilder importerBuilder = bundle("importer");
        importerBuilder.addImportPackage("foo", importing("[1,2)"));
        Resource importer = importerBuilder.build();

        assertThatThrownBy(() -> resolver.resolve(TestContext.resolving(host, importer)
                .offering(api("1.0.0"), api("2.0.0")).optionally(fragmentBuilder.build())))
                        .isInstanceOfSatisfying(UsesConstraintViolationException.class,
                                e -> assertThat(e.getResource()).isSameAs(host));
        assertThat(strategy.calls).isEqualTo(2);
    }

    @Test
    void aFragmentThatWasAskedForIsNeverLeftOut() {
        Resource host = hostImporting("[2,3)");
        Resource fragment = fragmentImporting("frag", "[1,2)");

        assertThatThrownBy(() -> resolver
                .resolve(TestContext.resolving(host, fragment).offering(api("1.0.0"), api("2.0.0"))))
                        .isInstanceOfSatisfying(FragmentConflictException.class, e -> {
                            assertThat(e.getFragment()).isSameAs(fragment);
                            assertThat(e.getHost()).isSameAs(host);
                            assertThat(e.getPackageName()).isEqualTo("api");
                        });
        assertThat(strategy.calls).isEqualTo(1);
    }

    @Test
    void aFragmentThatCanNotBeResolvedIsNotAttachedAndDoesNotRestartTheResolve() throws ResolutionException {
        Resource host = hostImporting("[1,2)");
        ResourceBuilder builder = fragment("frag", "1.0.0", "host");
        builder.addImportPackage("missing", importing("[1,2)"));
        Resource fragment = builder.build();

        Map<Resource, List<Wire>> wires = resolver
                .resolve(TestContext.resolving(host).offering(api("1.0.0")).optionally(fragment));

        assertThat(wires).containsKey(host).doesNotContainKey(fragment);
        assertThat(strategy.calls).isEqualTo(1);
    }

    @Test
    void aFragmentThatTakesNoPartInTheConflictIsKept() {
        ResourceBuilder exporter = bundle("exporter");
        exporter.addExportPackage("p", export("1.0.0", "api"));
        exporter.addImportPackage("api", importing("[1,2)"));
        ResourceBuilder hostBuilder = bundle("host");
        hostBuilder.addImportPackage("p", importing("[1,2)"));
        hostBuilder.addImportPackage("api", importing("[2,3)"));
        ResourceBuilder innocent = fragment("frag", "1.0.0", "host");
        innocent.addExportPackage("other", export("1.0.0"));

        assertThatThrownBy(() -> resolver.resolve(TestContext.resolving(hostBuilder.build()).offering(exporter.build(),
                api("1.0.0"), api("2.0.0")).optionally(innocent.build())))
                        .isInstanceOf(UsesConstraintViolationException.class);
        assertThat(strategy.calls).isEqualTo(1);
    }
}
