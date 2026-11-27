/*
 * Class space rules of the OSGi Core module layer (packages only).
 *
 * The scenario files generated from the Cucumber features open this module,
 * declare their bundles, exports and imports as `one sig`s and ask whether a valid resolution exists.
 * The rules are numbered R1.. in the comments below.
 */
module osgi

abstract sig Pkg {}

abstract sig Bundle {
	exports : set Export,
	imports : set Import
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
}

// what the bundle sees of a package: the provider of its imports or its own export if kept
fun view[r : Resolution, b : Bundle] : Pkg -> Export {
	{ p : Pkg, e : Export | b in r.resolved and (
		(some i : b.imports | i.pkg = p and e in i.(r.wire) and owner[e] != b)
		or (e in b.exports and e.pkg = p and e not in r.substituted)) }
}

// R5 uses: export x is implied by export s if the bundle of s sees the package that s uses from x
fun implied[r : Resolution] : Export -> Export {
	{ s, x : Export | some p : s.uses | x in view[r, owner[s]][p] }
}

pred mandatoryResolved[r : Resolution, m : set Bundle] { m in r.resolved }

// R5 a bundle sees every used package from the provider that the exporter sees
pred usesConsistent[r : Resolution] {
	all b : r.resolved, s : view[r, b][Pkg], x : s.^(implied[r]) |
		(some view[r, b][x.pkg]) implies view[r, b][x.pkg] = x
}

pred valid[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] and usesConsistent[r] }

// same without the uses constraint, used to tell a uses failure from a missing provider
pred validIgnoringUses[r : Resolution, m : set Bundle] { mandatoryResolved[r, m] }
