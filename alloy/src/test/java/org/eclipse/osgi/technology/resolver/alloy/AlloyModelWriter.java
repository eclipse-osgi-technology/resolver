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

/**
 * Writes the scenario of an {@link AlloyWorld} as an Alloy module that opens
 * {@code osgi.als}.
 */
class AlloyModelWriter {

    private static final String SCOPE = " for exactly 1 Resolution, 0 Pkg, 0 Bundle, 0 Export, 0 Import,"
            + " 0 MandatoryImport, 0 OptionalImport, 0 Require, 0 MandatoryRequire, 0 OptionalRequire\n";

    static String write(AlloyWorld world, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated for: ").append(title).append("\n");
        world.descriptions.forEach(d -> sb.append("//   ").append(d).append('\n'));
        sb.append("open osgi\n\n");

        List<String> pkgs = new ArrayList<>();
        for (BundleSpec b : world.bundles) {
            for (ExportSpec e : b.exports) {
                pkgs.add(e.pkg());
                pkgs.addAll(e.uses());
            }
            b.imports.forEach(i -> pkgs.add(i.pkg()));
        }
        pkgs.stream().distinct().forEach(p -> sb.append("one sig ").append(AlloyWorld.pkg(p)).append(" extends Pkg {}\n"));
        for (BundleSpec b : world.bundles) {
            sb.append("one sig ").append(b.atom).append(" extends Bundle {}\n");
            b.exports.forEach(e -> sb.append("one sig ").append(e.atom()).append(" extends Export {}\n"));
            b.imports.forEach(i -> sb.append("one sig ").append(i.atom()).append(" extends ")
                    .append(i.optional() ? "OptionalImport" : "MandatoryImport").append(" {}\n"));
            b.requires.forEach(q -> sb.append("one sig ").append(q.atom()).append(" extends ")
                    .append(q.optional() ? "OptionalRequire" : "MandatoryRequire").append(" {}\n"));
        }

        sb.append("\nfact {\n");
        for (BundleSpec b : world.bundles) {
            sb.append('\t').append(set(b.atom + ".exports", b.exports.stream().map(ExportSpec::atom).toList()))
                    .append('\n');
            sb.append('\t').append(set(b.atom + ".imports", b.imports.stream().map(ImportSpec::atom).toList()))
                    .append('\n');
            sb.append('\t').append(set(b.atom + ".requires", b.requires.stream().map(RequireSpec::atom).toList()))
                    .append('\n');
            for (RequireSpec q : b.requires) {
                sb.append('\t').append(set(q.atom() + ".matches", matches(world, q))).append('\n');
            }
            for (ExportSpec e : b.exports) {
                sb.append('\t').append(e.atom()).append(".pkg = ").append(AlloyWorld.pkg(e.pkg())).append('\n');
                sb.append('\t').append(set(e.atom() + ".uses", e.uses().stream().map(AlloyWorld::pkg).toList()))
                        .append('\n');
            }
            for (ImportSpec i : b.imports) {
                sb.append('\t').append(i.atom()).append(".pkg = ").append(AlloyWorld.pkg(i.pkg())).append('\n');
                sb.append('\t').append(set(i.atom() + ".matches", matches(world, i))).append('\n');
            }
        }
        List<String> reexported = world.bundles.stream().flatMap(b -> b.requires.stream())
                .filter(RequireSpec::reexport).map(RequireSpec::atom).toList();
        sb.append('\t').append(reexported.isEmpty() ? "no Reexported" : "Reexported = " + String.join(" + ", reexported))
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

    // the exports that satisfy name and version range of an import
    private static List<String> matches(AlloyWorld world, ImportSpec i) {
        List<String> matches = new ArrayList<>();
        for (BundleSpec b : world.bundles) {
            for (ExportSpec e : b.exports) {
                if (e.pkg().equals(i.pkg()) && (i.range() == null || i.range().includes(e.version()))) {
                    matches.add(e.atom());
                }
            }
        }
        return matches;
    }

    // the bundles that satisfy name and version range of a requirement
    private static List<String> matches(AlloyWorld world, RequireSpec q) {
        return world.bundles.stream()
                .filter(b -> b.name.equals(q.name()) && (q.range() == null || q.range().includes(b.version)))
                .map(b -> b.atom).toList();
    }

    private static String set(String lhs, List<String> atoms) {
        return atoms.isEmpty() ? "no " + lhs : lhs + " = " + String.join(" + ", atoms);
    }
}
