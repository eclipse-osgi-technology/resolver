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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.testsuite.BundleBuilder;
import org.eclipse.osgi.technology.resolver.testsuite.BundleRef;
import org.osgi.framework.Version;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;

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
        BundleBuilder builder = BundleBuilder.bundle(ref, new Attrs());
        BundleSpec b = new BundleSpec(ref.name(), ref.version() == null ? Version.emptyVersion : ref.version(), builder, "b" + bundles.size() + "_" + id(ref.name()));
        bundles.add(b);
        current = b;
    }

    void export(String pkg, Attrs attrs) {
        String uses = attrs.get("uses:");
        List<String> used = uses == null ? List.of() : List.of(uses.split("\\s*,\\s*"));
        current.builder.addExportPackage(pkg, attrs);
        current.exports.add(new ExportSpec("e" + counter++ + "_" + id(pkg), pkg, used));
    }

    void importPackage(String pkg, Attrs attrs) {
        current.builder.addImportPackage(pkg, attrs);
        current.imports.add(new ImportSpec("i" + counter++ + "_" + id(pkg), pkg, optional(attrs)));
    }

    void requireBundle(String name, Attrs attrs) {
        current.builder.addRequireBundle(name, attrs);
        current.requires.add(new RequireSpec("q" + counter++ + "_" + id(name), name, optional(attrs),
                "reexport".equals(attrs.get("visibility:"))));
    }

    void provideCapability(String ns, Attrs attrs) {
        String uses = attrs.get("uses:");
        current.builder.addProvideCapability(ns, attrs);
        current.provides.add(new CapabilitySpec("c" + counter++ + "_" + id(ns), ns,
                uses == null ? List.of() : List.of(uses.split("\\s*,\\s*"))));
    }

    /**
     * @param filter     the filter, {@code null} matches every capability of the namespace
     * @param directives the directives of the requirement
     */
    void requireCapability(String ns, String filter, Attrs directives) {
        if (filter == null) {
            Map<String, String> map = new HashMap<>();
            directives.forEach((k, v) -> map.put(k.endsWith(":") ? k.substring(0, k.length() - 1) : k, v));
            current.builder.addRequireAnyCapability(ns, map);
        } else {
            Attrs attrs = new Attrs(directives);
            attrs.put("filter:", filter);
            current.builder.addRequireCapability(ns, ns, attrs);
        }
        current.needs.add(new NeedSpec("n" + counter++ + "_" + id(ns), ns, optional(directives),
                "multiple".equals(directives.get("cardinality:"))));
    }

    private static boolean optional(Attrs attrs) {
        return "optional".equals(attrs.get("resolution:"));
    }

    /**
     * Parses the attributes and directives of a manifest clause, e.g.
     * {@code a=b;c:=d}.
     */
    static Attrs attrs(String clauseAttributes) {
        return new Parameters("x;" + clauseAttributes).get("x");
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

    /** The name of the atom of a namespace. */
    static String ns(String name) {
        return "ns_" + id(name);
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
