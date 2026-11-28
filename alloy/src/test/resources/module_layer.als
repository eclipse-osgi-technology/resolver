/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/*
 * Class space rules of the OSGi Core module layer: substitution, visibility of Require-Bundle and uses constraints.
 * The scenario files generated from the Cucumber features open resolver and module-layer,
 * declare their bundles, exports and imports as `one sig`s and ask whether a valid resolution exists.
 */
module module_layer

open resolver

// R4 an export is substituted when an import of the same package of its bundle is wired to another bundle
fact substitution {
    all r : Resolution | r.substituted = { e : Export | owner[e] in r.resolved and
        some i : owner[e].imports, x : i.(r.wire) | i.pkg = e.pkg and owner[x] != owner[e] }
}

// R9 the exports a bundle gets from its imports, wires to the own bundle (a kept export) included
fun imported[r : Resolution, b : Bundle, p : Pkg] : set Export {
    { e : Export | some i : b.imports | i.pkg = p and e in i.(r.wire) }
}

// R10 what requirers of the bundles cs get from them, a substituted export is replaced by the export the bundle imported instead
fun provided[r : Resolution, cs : set Bundle] : Pkg -> Export {
    { p : Pkg, e : Export | some c : cs, x : c.exports | c in r.resolved and x.pkg = p and
        ((x not in r.substituted and e = x) or (x in r.substituted and e in imported[r, c, p])) }
}

// reexport passes the required bundles of a bundle on to its requirers
fun reexp[r : Resolution] : Bundle -> Bundle {
    { c, d : Bundle | d in (c.requires & Reexported).(r.rwire) }
}

// the bundles whose exports a bundle sees through its requirements
fun space[r : Resolution, b : Bundle] : set Bundle {
    b.requires.(r.rwire) + b.requires.(r.rwire).^(reexp[r])
}

// R11 what the bundle sees of a package: imports first, then required bundles, then the own export if kept
fun view[r : Resolution, b : Bundle] : Pkg -> Export {
    { p : Pkg, e : Export | b in r.resolved and (
        (some imported[r, b, p]) => e in imported[r, b, p]
        else (some p.(provided[r, space[r, b]])) => e in p.(provided[r, space[r, b]])
        else (e in b.exports and e.pkg = p and e not in r.substituted)) }
}

// R5 uses: export x is implied by export s if the bundle of s sees the package that s uses from x
fun implied[r : Resolution] : Export -> Export {
    { s, x : Export | some p : s.uses | x in view[r, owner[s]][p] }
}

// R5 a bundle sees every used package from the provider that the exporter sees, a package split over required bundles has several
pred usesConsistent[r : Resolution] {
    all b : r.resolved, s : view[r, b][Pkg], x : s.^(implied[r]) |
        (some view[r, b][x.pkg]) implies x in view[r, b][x.pkg]
}

pred valid[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] and usesConsistent[r] }

// same without the uses constraint, used to tell a uses failure from a missing provider
pred validIgnoringUses[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] }
