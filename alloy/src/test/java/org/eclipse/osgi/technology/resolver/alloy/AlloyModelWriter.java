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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.resource.Capability;

/**
 * Writes the scenario of an {@link AlloyWorld} as an Alloy module that opens
 * {@code resolver.als} and {@code module_layer.als}.
 */
class AlloyModelWriter {

    private static final String SCOPE = " for exactly 1 Resolution, 0 Pkg, 0 Bundle, 0 Export, 0 Import,"
            + " 0 MandatoryImport, 0 OptionalImport, 0 Require, 0 MandatoryRequire, 0 OptionalRequire\n";

    static String write(AlloyWorld world, String title) {
        Matching matching = new Matching(world);
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated for: ").append(title).append("\n");
        world.descriptions.forEach(d -> sb.append("//   ").append(d).append('\n'));
        sb.append("open resolver\nopen module_layer\n\n");

        List<String> pkgs = new ArrayList<>();
        for (BundleSpec b : world.bundles) {
            for (ExportSpec e : b.exports) {
                pkgs.add(e.pkg());
                pkgs.addAll(e.uses());
            }
            imports(matching, b).forEach(i -> pkgs.add(i.pkg()));
        }
        pkgs.stream().distinct().forEach(p -> sb.append("one sig ").append(AlloyWorld.pkg(p)).append(" extends Pkg {}\n"));
        for (BundleSpec b : world.bundles) {
            sb.append("one sig ").append(b.atom).append(" extends Bundle {}\n");
            b.exports.forEach(e -> sb.append("one sig ").append(e.atom()).append(" extends Export {}\n"));
            imports(matching, b).forEach(i -> sb.append("one sig ").append(i.atom()).append(" extends ")
                    .append(i.optional() ? "OptionalImport" : "MandatoryImport").append(" {}\n"));
            b.requires.forEach(q -> sb.append("one sig ").append(q.atom()).append(" extends ")
                    .append(q.optional() ? "OptionalRequire" : "MandatoryRequire").append(" {}\n"));
        }

        sb.append("\nfact {\n");
        for (BundleSpec b : world.bundles) {
            sb.append("    ").append(set(b.atom + ".exports", b.exports.stream().map(ExportSpec::atom).toList()))
                    .append('\n');
            sb.append("    ").append(set(b.atom + ".imports", imports(matching, b).stream().map(ImportSpec::atom).toList()))
                    .append('\n');
            sb.append("    ").append(set(b.atom + ".requires", b.requires.stream().map(RequireSpec::atom).toList()))
                    .append('\n');
            for (RequireSpec q : b.requires) {
                sb.append("    ").append(set(q.atom() + ".matches", matches(matching, q))).append('\n');
            }
            for (ExportSpec e : b.exports) {
                sb.append("    ").append(e.atom()).append(".pkg = ").append(AlloyWorld.pkg(e.pkg())).append('\n');
                sb.append("    ").append(set(e.atom() + ".uses", e.uses().stream().map(AlloyWorld::pkg).toList()))
                        .append('\n');
            }
            for (ImportSpec i : imports(matching, b)) {
                sb.append("    ").append(i.atom()).append(".pkg = ").append(AlloyWorld.pkg(i.pkg())).append('\n');
                sb.append("    ").append(set(i.atom() + ".matches", matches(matching, i))).append('\n');
            }
        }
        List<String> reexported = world.bundles.stream().flatMap(b -> b.requires.stream())
                .filter(RequireSpec::reexport).map(RequireSpec::atom).toList();
        sb.append("    ").append(reexported.isEmpty() ? "no Reexported" : "Reexported = " + String.join(" + ", reexported))
                .append('\n');
        sb.append("}\n\n");

        String m = world.mandatory.isEmpty() ? "none"
                : world.mandatory.stream().map(b -> b.atom).collect(Collectors.joining(" + "));
        String asserted = world.assertions.stream().map(a -> " and (" + a + ")").collect(Collectors.joining());
        sb.append("run satisfiable { some r : Resolution | valid[r, ").append(m).append("]").append(asserted)
                .append(" }").append(SCOPE);
        sb.append("run validResolution { some r : Resolution | valid[r, ").append(m).append("] }").append(SCOPE);
        sb.append("run withoutUses { some r : Resolution | validIgnoringUses[r, ").append(m).append("] }")
                .append(SCOPE);
        return sb.toString();
    }

    // imports that are not effective at resolve time are ignored by a resolver
    private static List<ImportSpec> imports(Matching m, BundleSpec b) {
        return b.imports.stream().filter(m::effective).toList();
    }

    // the exports of a package requirement, found like a resolver asks its resolve context
    private static List<String> matches(Matching m, ImportSpec i) {
        List<String> matches = new ArrayList<>();
        for (Capability c : m.context.findProviders(m.requirement(i))) {
            matches.add(m.exports.get(c));
        }
        return matches;
    }

    // the bundles that provide a bundle requirement
    private static List<String> matches(Matching m, RequireSpec q) {
        List<String> matches = new ArrayList<>();
        for (Capability c : m.context.findProviders(m.requirement(q))) {
            matches.add(m.bundles.get(c.getResource()));
        }
        return matches;
    }

    private static String set(String lhs, List<String> atoms) {
        return atoms.isEmpty() ? "no " + lhs : lhs + " = " + String.join(" + ", atoms);
    }
}
