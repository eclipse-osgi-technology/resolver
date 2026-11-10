/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

class CapabilityIndexTest {

    private static final String PACKAGE = PackageNamespace.PACKAGE_NAMESPACE;

    private static Resource exporting(Object value, String... directives) throws Exception {
        ResourceBuilder builder = new ResourceBuilder();
        CapReqBuilder capability = new CapReqBuilder(PACKAGE).addAttribute(PACKAGE, value)
                .addAttribute("x", "1");
        for (int i = 0; i < directives.length; i += 2) {
            capability.addDirective(directives[i], directives[i + 1]);
        }
        builder.addCapability(capability);
        return builder.build();
    }

    private static Requirement importing(String filter) throws Exception {
        CapReqBuilder requirement = new CapReqBuilder(PACKAGE);
        if (filter != null) {
            requirement.addDirective("filter", filter);
        }
        return requirement.buildSyntheticRequirement();
    }

    private static Capability capability(Resource resource) {
        return resource.getCapabilities(PACKAGE).get(0);
    }

    @Test
    void findsProvidersByPrimaryValueInTheOrderOfTheResources() throws Exception {
        Resource a1 = exporting("a");
        Resource b = exporting("b");
        Resource a2 = exporting("a");
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(a1, b, a2));

        List<Capability> found = index.findProviders(importing("(&(osgi.wiring.package=a)(x=*))"));

        assertThat(found).containsExactly(capability(a1), capability(a2));
    }

    @Test
    void withoutFilterEverythingIsAProvider() throws Exception {
        Resource a = exporting("a");
        Resource b = exporting("b");
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(a, b));

        assertThat(index.findProviders(importing(null))).containsExactly(capability(a), capability(b));
    }

    @Test
    void capabilityWithSeveralValuesIsFoundUnderEachOfThem() throws Exception {
        Resource both = exporting(Arrays.asList("a", "b", "a"));
        Resource other = exporting("b");
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(both, other));

        assertThat(index.candidates("a")).containsExactly(capability(both));
        assertThat(index.candidates("b")).containsExactly(capability(both), capability(other));
        assertThat(index.all()).hasSize(2);
    }

    @Test
    void capabilityWithoutStringValueIsAlwaysACandidate() throws Exception {
        Resource a = exporting("a");
        Resource loose = exporting(42);
        Resource b = exporting("b");
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(a, loose, b));

        assertThat(index.candidates("a")).containsExactly(capability(a), capability(loose));
        assertThat(index.candidates("unknown")).containsExactly(capability(loose));
    }

    @Test
    void mandatoryAttributesHaveToBeRequested() throws Exception {
        Resource plain = exporting("a");
        Resource mandatory = exporting("a", "mandatory", "x");
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(plain, mandatory));

        assertThat(index.findProviders(importing("(osgi.wiring.package=a)"))).containsExactly(capability(plain));
        assertThat(index.findProviders(importing("(&(osgi.wiring.package=a)(x=1))")))
                .containsExactly(capability(plain), capability(mandatory));
    }

    @Test
    void invalidFilterIsRejected() throws Exception {
        CapabilityIndex index = new CapabilityIndex(PACKAGE, Arrays.asList(exporting("a")));

        assertThatThrownBy(() -> index.findProviders(importing("(osgi.wiring.package=a")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
