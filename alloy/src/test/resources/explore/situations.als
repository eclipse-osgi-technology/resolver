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
 * Exploration of the resolver rules without a fixed scenario: bundles, versions and ranges are free,
 * the candidates of the requirements are derived from them (specification mode).
 * `expect 0` means no instance or counterexample exists, `expect 1` means one exists.
 */
module situations

open resolver
open module_layer
open versions
open semantic_versions
open matching

sig P extends Pkg {}
sig B extends Bundle {}
sig E extends Export {}
sig MI extends MandatoryImport {}
sig OI extends OptionalImport {}
sig MQ extends MandatoryRequire {}
sig OQ extends OptionalRequire {}
sig V extends Version {}
sig Rg extends Range {}
sig N extends Name {}

fact { matchesFromVersions }

// the uses constraint rules out a resolution that is otherwise fine
run usesExcludesWiring {
    some r : Resolution | validIgnoringUses[r, none] and not valid[r, none]
} for 3 but 1 Resolution expect 1

// a bundle sees several exporters of a package through its required bundles (split package) and the resolution is still valid
run splitPackageIsValid {
    some r : Resolution, b : r.resolved, p : Pkg | valid[r, none] and #view[r, b][p] > 1
} for 4 but 1 Resolution expect 1

// finding: without the rule that a bundle does not import a package twice, a bundle can see two exporters
// of a package without requiring any bundle, the specification says that a bundle may not import a package twice
check singleProviderWithoutRequire {
    all r : Resolution, b : r.resolved, p : Pkg |
        (valid[r, none] and no b.requires) implies lone view[r, b][p]
} for 4 but 1 Resolution expect 1

// two exports of the same package in one bundle are allowed (3.6.5, e.g. different attributes for different importers),
// they are one class space, so the property is about the bundle that provides a package and not about the export
check singleProviderBundleWellFormed {
    wellFormed implies
        all r : Resolution, b : r.resolved, p : Pkg |
            (valid[r, none] and no b.requires) implies lone owner[view[r, b][p]]
} for 4 but 1 Resolution expect 0
