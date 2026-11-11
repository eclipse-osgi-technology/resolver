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
