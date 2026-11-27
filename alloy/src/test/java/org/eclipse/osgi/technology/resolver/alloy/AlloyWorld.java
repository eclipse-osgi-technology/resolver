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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.osgi.technology.resolver.testsuite.BundleRef;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * The state of one scenario. The steps only record what the scenario
 * describes and expects, the Alloy model is written and solved after the
 * scenario in {@link ResolveSteps}.
 */
public class AlloyWorld {

    final List<BundleSpec> bundles = new ArrayList<>();
    final List<String> assertions = new ArrayList<>();
    final List<String> descriptions = new ArrayList<>();
    List<BundleSpec> mandatory;
    Outcome outcome;
    boolean skipped;
    private BundleSpec current;
    private int counter;

    /**
     * Skips the scenario because a step is not expressed in the model yet.
     *
     * @param step what is missing
     */
    public void unsupported(String step) {
        skipped = true;
        assumeFalse(true, "not supported by the Alloy model yet: " + step);
    }

    void startBundle(BundleRef ref) {
        BundleSpec b = new BundleSpec(ref.name(), ref.version() == null ? Version.emptyVersion : ref.version(),
                "b" + bundles.size() + "_" + id(ref.name()));
        bundles.add(b);
        current = b;
    }

    void export(String pkg, String version, String uses) {
        List<String> used = uses == null ? List.of() : List.of(uses.split("\\s*,\\s*"));
        current.exports.add(new ExportSpec("e" + counter++ + "_" + id(pkg), pkg,
                version == null ? Version.emptyVersion : Version.parseVersion(version), used));
    }

    void importPackage(String pkg, String range, boolean optional) {
        current.imports.add(new ImportSpec("i" + counter++ + "_" + id(pkg), pkg,
                range == null ? null : VersionRange.valueOf(range), optional));
    }

    /**
     * Finds the bundle a reference names.
     *
     * @return the bundle, the scenario is skipped if it is not unique
     */
    BundleSpec find(BundleRef ref) {
        List<BundleSpec> found = bundles.stream()
                .filter(b -> b.name.equals(ref.name()) && (ref.version() == null || b.version.equals(ref.version())))
                .toList();
        if (found.size() != 1) {
            unsupported(ref + " is not unique");
        }
        return found.get(0);
    }

    /** The name of the atom of a package. */
    static String pkg(String name) {
        return "p_" + id(name);
    }

    private static String id(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** Records an Alloy formula over the resolution {@code r} that has to hold. */
    void assertThat(String description, String formula) {
        descriptions.add(description);
        assertions.add(formula);
    }
}
