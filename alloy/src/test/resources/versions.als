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
 * OSGi versions and version ranges (Core specification, Module Layer: Version, Version Ranges).
 * The qualifier is left out, it has no compatibility meaning (see semantic_versions.als) and does not change the shape of the rules.
 * Alloy integers are bounded, runs have to give a bit width that fits the largest version part (7 bits allow 63).
 */
module versions

abstract sig Version {
    major : one Int,
    minor : one Int,
    micro : one Int
}

// versions are numbers, so two atoms are never the same version
fact versionsAreNumbers {
    all v : Version | v.major >= 0 and v.minor >= 0 and v.micro >= 0
    all disj a, b : Version | a.major != b.major or a.minor != b.minor or a.micro != b.micro
}

/*
 * A range has a floor and an optional ceiling (no ceiling is infinite, a plain version "1.2" is the range [1.2,infinite)).
 * Whether the ends are part of the range is the subset signatures below, every file that declares ranges has to fix them.
 */
abstract sig Range {
    floor   : one Version,
    ceiling : lone Version
}

sig InclusiveFloor in Range {}
sig InclusiveCeiling in Range {}

pred older[a, b : Version] {
    a.major < b.major or (a.major = b.major and (a.minor < b.minor or (a.minor = b.minor and a.micro < b.micro)))
}

pred includes[r : Range, v : Version] {
    (v = r.floor and r in InclusiveFloor or older[r.floor, v]) and
    (no r.ceiling or (v = r.ceiling and r in InclusiveCeiling) or older[v, r.ceiling])
}
