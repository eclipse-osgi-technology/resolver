#
# Copyright (c) 2026 Contributors to the Eclipse Foundation.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Specification (importing and exporting the same package):
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module-import.export.same.package
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.requirebundle
Feature: Substitution of exported packages
  A bundle that exports and imports the same package lets the resolver decide where the package comes from.
  If the import is wired to another bundle, the own export is discarded (substituted) and everybody that gets the package through this bundle gets it from the other bundle too.
  If the import would be wired to the bundle itself, the export is kept and there is no wire for the import.
  Among matching providers the highest version is preferred.

  Scenario: An export without a better provider is kept
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.0,2.0)"
    When I resolve bundle "a"
    Then the resolution succeeds
    And bundle "a" keeps its export of package "p"
    And bundle "a" has 0 wires

  Scenario: An export is substituted if another bundle offers a higher version
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    When I resolve bundle "a"
    Then the resolution succeeds
    And bundle "a" substitutes its export of package "p"
    And bundle "a" is wired to bundle "b" for package "p"

  Scenario: An export is kept if it is the highest version
    Given there is bundle "a"
    And it exports package "p" in version "1.1.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.0.0"
    When I resolve bundle "a" and bundle "b"
    Then the resolution succeeds
    And bundle "a" keeps its export of package "p"
    And bundle "a" is not wired to bundle "b"

  Scenario: A bundle that only exports a package never substitutes it
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    And there is bundle "c"
    And it imports package "p" in version range "[1.0,1.1)"
    When I resolve bundle "c"
    Then the resolution succeeds
    And bundle "c" is wired to bundle "a" for package "p"
    And bundle "a" has 0 wires

  Scenario: An optional overlapping import is substituted when a provider is available
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it optionally imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    When I resolve bundle "a"
    Then the resolution succeeds
    And bundle "a" substitutes its export of package "p"

  Scenario: Both bundles exporting and importing the same package agree on one provider
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    And it imports package "p" in version range "[1.0,2.0)"
    When I resolve bundle "a" and bundle "b"
    Then the resolution succeeds
    And bundle "a" substitutes its export of package "p"
    And bundle "b" keeps its export of package "p"
    And bundle "a" is wired to bundle "b" for package "p"

  Scenario: Substitution chains lead to the same provider for all
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "c"
    And it exports package "p" in version "1.2.0"
    When I resolve bundle "a" and bundle "b"
    Then the resolution succeeds
    And bundle "a" is wired to bundle "c" for package "p"
    And bundle "b" is wired to bundle "c" for package "p"

  Scenario: An export that another bundle imports can not be substituted
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.0,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    And there is bundle "d"
    And it imports package "p" in version range "[1.0,1.1)"
    When I resolve bundle "d" and bundle "a"
    Then the resolution succeeds
    And bundle "d" is wired to bundle "a" for package "p"
    And bundle "a" keeps its export of package "p"

  Scenario: An import that can only be satisfied by discarding the export it depends on fails
    Given there is bundle "a"
    And it exports package "p" in version "1.0.0"
    And it imports package "p" in version range "[1.1,2.0)"
    And there is bundle "b"
    And it exports package "p" in version "1.1.0"
    And there is bundle "d"
    And it imports package "p" in version range "[1.0,1.1)"
    When I resolve bundle "d" and bundle "a"
    Then the resolution fails because the import of package "p" of bundle "d" is hidden by a substituted export

  Scenario: Substitution is forced by the uses constraint of another export
    Given there is bundle "c"
    And it exports package "api" in version "1.1.0"
    And there is bundle "a"
    And it exports package "api" in version "1.2.0"
    And it imports package "api" in version range "[1.0,2.0)"
    And it exports package "util" using "api"
    And there is bundle "d"
    And it imports package "util"
    And it imports package "api" in version range "[1.1,1.2)"
    When I resolve bundle "d"
    Then the resolution succeeds
    And bundle "d" is wired to bundle "a" for package "util"
    And bundle "d" is wired to bundle "c" for package "api"
    And bundle "a" substitutes its export of package "api"
    And bundle "a" is wired to bundle "c" for package "api"

  Scenario: Substitution can not resolve a uses conflict if the import range does not allow it
    Given there is bundle "c"
    And it exports package "api" in version "1.1.0"
    And there is bundle "a"
    And it exports package "api" in version "1.2.0"
    And it imports package "api" in version range "[1.2,2.0)"
    And it exports package "util" using "api"
    And there is bundle "d"
    And it imports package "util"
    And it imports package "api" in version range "[1.1,1.2)"
    When I resolve bundle "d"
    Then the resolution fails because of a uses constraint violation on package "api" for bundle "d"

  Rule: A bundle that requires a bundle sees the package the required bundle is wired to
    If an exported package of a required bundle is substituted by an import, the requiring bundles are wired to the same exported package that the required bundle is wired to.
    The same holds for bundles that reach it through reexport.

    Scenario: A requiring bundle sees the export of the required bundle if it is kept
      Given there is bundle "a"
      And it exports package "p" in version "1.1.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.0.0"
      And there is bundle "d"
      And it requires bundle "a"
      When I resolve bundle "d" and bundle "b"
      Then the resolution succeeds
      And bundle "d" sees package "p" from bundle "a"

    Scenario: A requiring bundle sees the substituted package of the required bundle
      Given there is bundle "a"
      And it exports package "p" in version "1.0.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.1.0"
      And there is bundle "d"
      And it requires bundle "a"
      When I resolve bundle "d"
      Then the resolution succeeds
      And bundle "a" substitutes its export of package "p"
      And bundle "d" sees package "p" from bundle "b"

    Scenario: A reexporting bundle passes on the substituted package
      Given there is bundle "a"
      And it exports package "p" in version "1.0.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.1.0"
      And there is bundle "mid"
      And it requires bundle "a" and reexports it
      And there is bundle "top"
      And it requires bundle "mid"
      When I resolve bundle "top"
      Then the resolution succeeds
      And bundle "top" sees package "p" from bundle "b"

    Scenario: An export with a mandatory attribute is substituted by an import without it
      Given there is bundle "a"
      And it exports package "p" in version "1.0.0" with "x=1;mandatory:=x"
      And it imports package "p"
      And there is bundle "b"
      And it exports package "p" in version "1.0.0"
      And there is bundle "d"
      And it requires bundle "a"
      When I resolve bundle "d"
      Then the resolution succeeds
      And bundle "a" substitutes its export of package "p"
      And bundle "d" sees package "p" from bundle "b"

    Scenario: A required bundle that exports and imports the same packages provides them to all requirers
      Three bundles export and import the packages "x" and "y" and one bundle requires each of them.
      All requirers see the packages from the first bundle, as the other two substitute their exports.
      Given there is bundle "A"
      And it exports package "x" in version "1.0.0"
      And it exports package "y" in version "1.0.0"
      And it exports package "z" in version "1.0.0"
      And it imports package "x" in version range "1.0.0"
      And it imports package "y" in version range "1.0.0"
      And there is bundle "B"
      And it exports package "x" in version "1.0.0"
      And it exports package "y" in version "1.0.0"
      And it imports package "x" in version range "1.0.0"
      And it imports package "y" in version range "1.0.0"
      And there is bundle "C"
      And it exports package "x" in version "1.0.0"
      And it exports package "y" in version "1.0.0"
      And it imports package "x" in version range "1.0.0"
      And it imports package "y" in version range "1.0.0"
      And there is bundle "D"
      And it requires bundle "A"
      And there is bundle "E"
      And it requires bundle "B"
      And there is bundle "F"
      And it requires bundle "C"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "A" keeps its export of package "x"
      And bundle "A" keeps its export of package "y"
      And bundle "B" substitutes its export of package "x"
      And bundle "B" substitutes its export of package "y"
      And bundle "C" substitutes its export of package "x"
      And bundle "C" substitutes its export of package "y"
      And bundle "D" sees package "x" from bundle "A"
      And bundle "D" sees package "y" from bundle "A"
      And bundle "D" sees package "z" from bundle "A"
      And bundle "E" sees package "x" from bundle "A"
      And bundle "E" sees package "y" from bundle "A"
      And bundle "F" sees package "x" from bundle "A"
      And bundle "F" sees package "y" from bundle "A"

    Scenario: A bundle keeps the export it substitutes when a requirer of it needs it for a uses constraint
      The requirer requires "a", that substitutes "p" by the higher version of "b", and imports "q" that uses "p" from the lower version.
      The conflict is avoided by "a" keeping its export, as the provider of "q" can import it from there.
      Given there is bundle "a"
      And it exports package "p" in version "1.0.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.1.0"
      And there is bundle "e"
      And it imports package "p" in version range "[1.0,1.1)"
      And it exports package "q" using "p"
      And there is bundle "d"
      And it requires bundle "a"
      And it imports package "q"
      When I resolve bundle "d"
      Then the resolution succeeds
      And bundle "d" sees package "p" from bundle "a"
      And bundle "e" is wired to bundle "a" for package "p"

  Rule: The exports of a fragment are substituted or kept by its host
    The fragment contributes its exports and imports to the host, so the host is wired for the substituted package.

    Scenario: Substituted exports of a fragment are wired on the host
      Given there is bundle "host"
      And there is a fragment bundle "frag" of bundle "host"
      And it exports package "p" in version "1.0.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.1.0"
      When I resolve bundle "host" and bundle "frag"
      Then the resolution succeeds
      And bundle "host" is wired to bundle "b" for package "p"

    Scenario: The export of a fragment that another bundle needs is kept by the host
      Given there is bundle "host"
      And there is a fragment bundle "frag" of bundle "host"
      And it exports package "p" in version "1.0.0"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "b"
      And it exports package "p" in version "1.1.0"
      And there is bundle "consumer"
      And it imports package "p" in version range "[1.0,1.1)"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "host" for package "p"

  Rule: Of several bundles that export and import the same package only the needed export is used
    Substituting an export removes a provider from the resolve, so it is only sound if nobody needs that provider.
    The specification warns that substitution "can lead to cases where consumers for a version of the current bundle become unresolvable" and that it "should be avoided and only used in rare cases".

    Scenario: Bundles that export and import the same package in the same version keep exactly one export
      Given there is bundle "libg"
      And it exports package "p" in version "3.0.0"
      And it imports package "p" in version range "[3.0,4.0)"
      And there is bundle "util"
      And it exports package "p" in version "3.0.0"
      And it imports package "p" in version range "[3.0,4.0)"
      And there is bundle "consumer"
      And it imports package "p" in version range "[3.0,4.0)"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "libg" keeps its export of package "p"
      And bundle "util" substitutes its export of package "p"
      And bundle "util" is wired to bundle "libg" for package "p"
      And bundle "consumer" is wired to bundle "libg" for package "p"

    Scenario: Only the bundle that is needed is resolved if both could provide the same package
      Given there is bundle "libg"
      And it exports package "p" in version "3.0.0"
      And it imports package "p" in version range "[3.0,4.0)"
      And there is bundle "util"
      And it exports package "p" in version "3.0.0"
      And it imports package "p" in version range "[3.0,4.0)"
      And there is bundle "consumer"
      And it imports package "p" in version range "[3.0,4.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "libg" for package "p"
      And bundle "util" is not resolved

    Scenario: An import without a version range lets an export be substituted by an equal export of another bundle
      Given there is bundle "base"
      And it exports package "p" in version "1.3.0"
      And there is bundle "supplement"
      And it exports package "p" in version "1.3.0"
      And it imports package "p"
      And there is bundle "consumer"
      And it imports package "p"
      When I resolve bundle "supplement" and bundle "consumer"
      Then the resolution succeeds
      And bundle "supplement" substitutes its export of package "p"
      And bundle "supplement" is wired to bundle "base" for package "p"
      And bundle "consumer" is wired to bundle "base" for package "p"

    Scenario: Open ended import ranges substitute the lower export by the higher one
      Given there is bundle "newer"
      And it exports package "p" in version "3.0.4"
      And it imports package "p" in version range "3.0.4"
      And there is bundle "older"
      And it exports package "p" in version "3.0.0"
      And it imports package "p" in version range "3.0.0"
      And there is bundle "consumer"
      And it requires bundle "older"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "newer" keeps its export of package "p"
      And bundle "older" substitutes its export of package "p"
      And bundle "consumer" sees package "p" from bundle "newer"

    Scenario: Exports of different major versions are all kept if the import ranges do not overlap
      Given there is bundle "lib-1"
      And it exports package "p" in version "1.45.0"
      And it imports package "p" in version range "[1.45,2.0)"
      And there is bundle "lib-2"
      And it exports package "p" in version "2.0.0"
      And it imports package "p" in version range "[2.0,3.0)"
      And there is bundle "consumer-1"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-2"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "lib-1" keeps its export of package "p"
      And bundle "lib-2" keeps its export of package "p"
      And bundle "consumer-1" is wired to bundle "lib-1" for package "p"
      And bundle "consumer-2" is wired to bundle "lib-2" for package "p"

    Scenario: A consumer that needs the lower version keeps the export even if the import has no version range
      Given there is bundle "impl"
      And it exports package "api" in version "1.0.0"
      And it imports package "api"
      And there is bundle "api-2"
      And it exports package "api" in version "2.0.0"
      And there is bundle "old-consumer"
      And it imports package "api" in version range "[1.0,2.0)"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "impl" keeps its export of package "api"
      And bundle "old-consumer" is wired to bundle "impl" for package "api"

    Scenario: Consumers that need different versions are served by different bundles and nothing is substituted
      Given there is bundle "impl"
      And it exports package "api" in version "1.0.0"
      And it imports package "api" in version range "[1.0,2.0)"
      And there is bundle "api-1.1"
      And it exports package "api" in version "1.1.0"
      And there is bundle "old-consumer"
      And it imports package "api" in version range "[1.0,1.1)"
      And there is bundle "new-consumer"
      And it imports package "api" in version range "[1.1,2.0)"
      When I resolve all bundles
      Then the resolution succeeds
      And bundle "impl" keeps its export of package "api"
      And bundle "old-consumer" is wired to bundle "impl" for package "api"
      And bundle "new-consumer" is wired to bundle "api-1.1" for package "api"

    Scenario: A substitution that nobody needs is made, the export of the substituted bundle is not used
      Given there is bundle "impl"
      And it exports package "api" in version "1.0.0"
      And it imports package "api" in version range "[1.0,2.0)"
      And there is bundle "api-1.1"
      And it exports package "api" in version "1.1.0"
      And there is bundle "consumer"
      And it imports package "api" in version range "[1.0,2.0)"
      When I resolve bundle "impl" and bundle "consumer"
      Then the resolution succeeds
      And bundle "impl" substitutes its export of package "api"
      And bundle "consumer" is wired to bundle "api-1.1" for package "api"

    Scenario: A consumer that is only needed indirectly keeps the export it depends on
      Given there is bundle "api-1.1"
      And it exports package "api" in version "1.1.0"
      And there is bundle "impl"
      And it exports package "api" in version "1.0.0"
      And it imports package "api" in version range "[1.0,2.0)"
      And there is bundle "old-consumer"
      And it imports package "api" in version range "[1.0,1.1)"
      And there is bundle "root"
      And it requires bundle "old-consumer"
      When I resolve bundle "root"
      Then the resolution succeeds
      And bundle "old-consumer" is wired to bundle "impl" for package "api"
      And bundle "impl" keeps its export of package "api"

    Scenario: A consumer that has to have the substituted export fails the resolve if it is needed together with the substitution
      Given there is bundle "api-1.1"
      And it exports package "api" in version "1.1.0"
      And there is bundle "impl"
      And it exports package "api" in version "1.0.0"
      And it imports package "api" in version range "[1.0,2.0)"
      And it exports package "util" using "api"
      And there is bundle "user"
      And it imports package "util"
      And it imports package "api" in version range "[1.1,2.0)"
      And there is bundle "old-consumer"
      And it imports package "api" in version range "[1.0,1.1)"
      When I resolve bundle "user" and bundle "old-consumer"
      Then the resolution fails because of a uses constraint violation on package "api" for bundle "user"

  Rule: A substitutable export can be wired to either of two exporters but not to both at once
    Background:
      Given there is bundle "A"
      And it exports package "util" in version "1.0.0" using "internal"
      And it exports package "internal" in version "1.0.0"
      And it imports package "internal" in version range "[1.0,2.0)"
      And there is bundle "A2"
      And it exports package "internal" in version "1.1.0"

    Scenario: A consumer that needs the own version of the internal package is served by A
      Given there is bundle "B"
      And it imports package "util" in version range "[1.0.0,1.0.0]"
      And it imports package "internal" in version range "[1.0.0,1.0.0]"
      When I resolve bundle "B"
      Then the resolution succeeds
      And bundle "B" is wired to bundle "A" for package "util"
      And bundle "B" is wired to bundle "A" for package "internal"

    Scenario: A consumer that needs the substituting version of the internal package is served by A2
      Given there is bundle "C"
      And it imports package "util" in version range "[1.0.0,2.0.0)"
      And it imports package "internal" in version range "[1.1,2.0)"
      When I resolve bundle "C"
      Then the resolution succeeds
      And bundle "C" is wired to bundle "A" for package "util"
      And bundle "C" is wired to bundle "A2" for package "internal"
      And bundle "A" is wired to bundle "A2" for package "internal"
