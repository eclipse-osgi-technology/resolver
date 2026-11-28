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
 * Exploration of the semantic versioning rules, independent of any scenario.
 * `expect 0` means no instance or counterexample exists, `expect 1` means one exists.
 */
module semantic_versions_explore

open versions
open semantic_versions

sig V extends Version {}
sig R extends Range {}

// a consumer range accepts exactly the versions that are backward compatible
check consumerRangeIsBackwardCompatible {
    all r : R, v, x : V | consumerRange[r, v] implies (includes[r, x] iff backwardCompatible[v, x])
} for 4 but 7 int expect 0

// a provider range accepts exactly the versions that only differ in the micro part
check providerRangeIsMicroOnly {
    all r : R, v, x : V | providerRange[r, v] implies (includes[r, x] iff providerCompatible[v, x])
} for 4 but 7 int expect 0

// everything a provider accepts a consumer accepts as well
check providerRangeIsNarrower {
    all p, c : R, v, x : V | providerRange[p, v] and consumerRange[c, v] and includes[p, x] implies includes[c, x]
} for 4 but 7 int expect 0

// a minor update is accepted by a consumer but not by a provider
run minorUpdateAsymmetry {
    some disj p, c : R, v, x : V | providerRange[p, v] and consumerRange[c, v] and
        includes[c, x] and not includes[p, x]
} for 4 but 7 int expect 1

// a range that excludes its own floor and ceiling is a range as well, and can be empty
run emptyRange {
    some r : R, v : V | r not in InclusiveFloor and r not in InclusiveCeiling and
        some r.ceiling and older[r.floor, r.ceiling] and not includes[r, v] and
        older[r.floor, v] and older[v, r.ceiling]
} for 4 but 7 int expect 0
