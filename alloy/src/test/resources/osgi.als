/*
 * Class space rules of the OSGi Core module layer (Import-Package, Export-Package and Require-Bundle).
 *
 * The scenario files generated from the Cucumber features open this module,
 * declare their bundles, exports and imports as `one sig`s and ask whether a valid resolution exists.
 * The rules are numbered R1.. in the comments below.
 */
module osgi

abstract sig Pkg {}

abstract sig Bundle {
	exports  : set Export,
	imports  : set Import,
	requires : set Require
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
	substituted : set Export
} {
	// R1 only resolved bundles have wires
	wire.Export in resolved.imports
	// R2 an import is wired to a matching export of a resolved bundle that is not substituted
	all i : Import, e : i.wire | e in i.matches and owner[e] in resolved and e not in substituted
	// R3 a mandatory import of a resolved bundle needs a wire
	all b : resolved, i : b.imports | i in MandatoryImport implies some i.wire
	// R4 an export is substituted when an import of the same package of its bundle is wired to another bundle
	substituted = { e : Export | owner[e] in resolved and
		some i : owner[e].imports, x : i.wire | i.pkg = e.pkg and owner[x] != owner[e] }
	// R6 only resolved bundles have require wires
	rwire.Bundle in resolved.requires
	// R7 a requirement is wired to a matching resolved bundle
	all q : Require, b : q.rwire | b in q.matches and b in resolved
	// R8 a mandatory requirement of a resolved bundle needs a wire
	all b : resolved, q : b.requires | q in MandatoryRequire implies some q.rwire
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

pred mandatoryResolved[r : Resolution, m : set Bundle] { m in r.resolved }

// R5 a bundle sees every used package from the provider that the exporter sees, a package split over required bundles has several
pred usesConsistent[r : Resolution] {
	all b : r.resolved, s : view[r, b][Pkg], x : s.^(implied[r]) |
		(some view[r, b][x.pkg]) implies x in view[r, b][x.pkg]
}

pred valid[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] and usesConsistent[r] }

// same without the uses constraint, used to tell a uses failure from a missing provider
pred validIgnoringUses[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] }
