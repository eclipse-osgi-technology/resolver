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
 * Specification mode: derives the candidates of a requirement (the `matches` of the resolver model) from names and versions.
 * The scenarios do not need it, there the candidates are given by the resolve context.
 * It is used to explore the rules without a fixed input, and to cross check the candidates of the resolve context
 * against the rules of the specification for the scenarios that only use names and version ranges.
 * Attributes, filters and directives such as mandatory are not part of it.
 */
module matching

open resolver
open versions

abstract sig Name {}

one sig Versioning {
    exportVersion : Export -> one Version,
    importRange   : Import -> lone Range, // no range matches every version
    bundleName    : Bundle -> one Name,
    bundleVersion : Bundle -> one Version,
    requireName   : Require -> one Name,
    requireRange  : Require -> lone Range
}

// an export satisfies an import if the package is the same and the version is in the range
fun importMatches[i : Import] : set Export {
    { e : Export | e.pkg = i.pkg and
        (no Versioning.importRange[i] or includes[Versioning.importRange[i], Versioning.exportVersion[e]]) }
}

// a bundle satisfies a requirement if the symbolic name is the same and the bundle version is in the range
fun requireMatches[q : Require] : set Bundle {
    { b : Bundle | Versioning.bundleName[b] = Versioning.requireName[q] and
        (no Versioning.requireRange[q] or includes[Versioning.requireRange[q], Versioning.bundleVersion[b]]) }
}

pred matchesFromVersions {
    all i : Import | i.matches = importMatches[i]
    all q : Require | q.matches = requireMatches[q]
}
