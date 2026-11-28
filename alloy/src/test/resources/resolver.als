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
 * Resolver model: bundles with exports, imports and requires, and the wires of a candidate resolution that satisfy the requirements.
 * The class space rules (substitution, uses, visibility) are in module_layer.als.
 *
 * The scenario files generated from the Cucumber features open this module,
 * declare their bundles, exports and imports as `one sig`s and ask whether a valid resolution exists.
 * The rules are numbered R1.. in the comments below.
 */
module resolver

abstract sig Pkg {}

abstract sig Bundle {
    exports  : set Export,
    imports  : set Import,
    requires : set Require,
    provides : set Capability, // capabilities of namespaces that have no dedicated rules, e.g. osgi.contract or custom ones
    needs    : set Requirement
}

abstract sig Export {
    pkg  : one Pkg,
    uses : set Pkg
}

abstract sig Import {
    pkg     : one Pkg,
    matches : set Export // exports that satisfy package name, version range and attributes, precomputed by the generator
}

abstract sig OptionalImport extends Import {}
abstract sig MandatoryImport extends Import {}

abstract sig Require {
    matches : set Bundle // bundles that satisfy symbolic name and version range, precomputed by the generator
}

abstract sig OptionalRequire extends Require {}
abstract sig MandatoryRequire extends Require {}

abstract sig Namespace {}

abstract sig Capability {
    uses : set Pkg
}

abstract sig Requirement {
    ns      : one Namespace,
    matches : set Capability // capabilities of the namespace that satisfy the filter, precomputed by the generator
}

abstract sig OptionalRequirement extends Requirement {}
abstract sig MandatoryRequirement extends Requirement {}

// requirements with cardinality:=multiple, they are wired to every available provider
sig Multiple in Requirement {}

// requirements with visibility:=reexport, a subset because it is orthogonal to optional
sig Reexported in Require {}

// the exporting bundle of an export
fun owner[e : Export] : Bundle { exports.e }

// the importing bundle of an import
fun importer[i : Import] : Bundle { imports.i }

/*
 * One candidate outcome of a resolve.
 * `wire` maps imports to the export that provides them, a wire to an export of the own bundle is a self wire (the export is kept).
 */
sig Resolution {
    resolved    : set Bundle,
    wire        : Import -> lone Export,
    rwire       : Require -> lone Bundle,
    gwire       : Requirement -> set Capability,
    substituted : set Export // defined by module_layer.als
} {
    // R1 only resolved bundles have wires
    wire.Export in resolved.imports
    // R2 an import is wired to a matching export of a resolved bundle that is not substituted
    all i : Import, e : i.wire | e in i.matches and owner[e] in resolved and e not in substituted
    // R3 a mandatory import of a resolved bundle needs a wire
    all b : resolved, i : b.imports | i in MandatoryImport implies some i.wire
    // R6 only resolved bundles have require wires
    rwire.Bundle in resolved.requires
    // R7 a requirement is wired to a matching resolved bundle
    all q : Require, b : q.rwire | b in q.matches and b in resolved
    // R8 a mandatory requirement of a resolved bundle needs a wire
    all b : resolved, q : b.requires | q in MandatoryRequire implies some q.rwire
    // R12 only resolved bundles have wires for generic requirements
    gwire.Capability in resolved.needs
    // R13 a generic requirement is wired to matching capabilities of resolved bundles
    all q : Requirement, c : q.gwire | c in q.matches and c in resolved.provides
    // R14 a mandatory generic requirement of a resolved bundle needs a wire
    all b : resolved, q : b.needs | q in MandatoryRequirement implies some q.gwire
    // R15 a requirement is wired to one capability, with multiple cardinality to every available one
    all q : Requirement - Multiple | lone q.gwire
    all q : (resolved.needs & Multiple) | q.gwire = q.matches & resolved.provides
}

pred mandatoryResolved[r : Resolution, m : set Bundle] { m in r.resolved }
