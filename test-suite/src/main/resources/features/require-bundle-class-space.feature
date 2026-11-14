#
# Copyright (c) 2026 Contributors to the Eclipse Foundation.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Specification:
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.requirebundle
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.constraintsolving
Feature: Require-Bundle class space
  A bundle that requires a bundle sees all exported packages of it, and of the bundles it reexports.
  Everything it sees of one package has to be consistent, a package that is split over several bundles is one class space.

  Scenario: A package of a required bundle is seen by the requirer
    Given there is bundle "provider"
    And it exports package "p"
    And there is bundle "consumer"
    And it requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "provider"
    And bundle "consumer" has 1 wire

  Scenario: A package of a bundle required through a reexport is seen
    Given there is bundle "root"
    And it exports package "p"
    And there is bundle "middle"
    And it requires bundle "root" and reexports it
    And there is bundle "consumer"
    And it requires bundle "middle"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "root"

  Scenario: A package of a bundle that is required privately is not seen through the requirer
    Given there is bundle "root"
    And it exports package "p"
    And there is bundle "middle"
    And it requires bundle "root"
    And there is bundle "consumer"
    And it requires bundle "middle"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 1 wire
    And bundle "consumer" is wired to bundle "middle"
    And bundle "consumer" is not wired to bundle "root"

  Scenario: A package split over two required bundles is accepted
    Given there is bundle "first"
    And it exports package "p"
    And there is bundle "second"
    And it exports package "p"
    And there is bundle "consumer"
    And it requires bundle "first"
    And it requires bundle "second"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 2 wires

  Scenario: An import shadows the package of a required bundle
    Given there is bundle "imported"
    And it exports package "p"
    And there is bundle "required"
    And it exports package "p"
    And there is bundle "consumer"
    And it requires bundle "required"
    And it imports package "p"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "imported"

  Scenario: An optionally required bundle with a reexport is seen when it is available
    Given there is bundle "root"
    And it exports package "p"
    And there is bundle "middle"
    And it optionally requires bundle "root" and reexports it
    And there is bundle "consumer"
    And it requires bundle "middle"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "root"

  Scenario: A required bundle version range selects the highest bundle of the range
    Given there is bundle "provider" version 1.0.0
    And it exports package "p"
    And there is bundle "provider" version 2.0.0
    And it exports package "p"
    And there is bundle "provider" version 3.0.0
    And it exports package "p"
    And there is bundle "consumer"
    And it requires bundle "provider" in version range "[1.0,3.0)"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "provider" version 2.0.0

  Scenario: A required package is inconsistent with what an imported package uses
    The consumer sees "p" from "required", but the exporter of "q" is wired to another provider of "p".
    Given there is bundle "required"
    And it exports package "p"
    And there is bundle "other"
    And it exports package "p" in version "2.0.0"
    And there is bundle "user"
    And it imports package "p" in version range "[2.0,3.0)"
    And it exports package "q" using "p"
    And there is bundle "consumer"
    And it requires bundle "required"
    And it imports package "q"
    When I resolve bundle "consumer"
    Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

  Scenario: The provider of an import is the required bundle when the exporter has a choice
    The exporter of "q" imports "p" from whichever provider is free to choose, it has to take the one the consumer requires.
    Given there is bundle "required"
    And it exports package "p"
    And there is bundle "other"
    And it exports package "p" in version "2.0.0"
    And there is bundle "user"
    And it imports package "p"
    And it exports package "q" using "p"
    And there is bundle "consumer"
    And it requires bundle "required"
    And it imports package "q"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "user" is wired to bundle "required" for package "p"

  Scenario: A required bundle is chosen so that the class space is consistent
    The highest "required" bundle exports "p" from another provider than the exporter of "q" can use.
    Given there is bundle "required" version 1.0.0
    And it exports package "p" in version "1.0.0"
    And there is bundle "required" version 2.0.0
    And it exports package "p" in version "2.0.0"
    And there is bundle "user"
    And it imports package "p" in version range "[1.0,2.0)"
    And it exports package "q" using "p"
    And there is bundle "consumer"
    And it requires bundle "required"
    And it imports package "q"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" sees package "p" from bundle "required" version 1.0.0

  Scenario: An import is wired to the provider that is also seen through a reexported bundle
    The versioned provider of "dom" is preferred, but the consumer sees the unversioned one through "platform", which reexports "jre".
    Given there is bundle "jre"
    And it exports package "dom"
    And there is bundle "xml-apis"
    And it exports package "dom" in version "1.4.0"
    And there is bundle "svg"
    And it imports package "dom"
    And it exports package "svg" in version "1.1.0" using "dom"
    And there is bundle "platform"
    And it requires bundle "jre" and reexports it
    And there is bundle "consumer"
    And it requires bundle "platform"
    And it imports package "svg"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "svg" is wired to bundle "jre" for package "dom"
    And bundle "consumer" sees package "dom" from bundle "jre"
    And bundle "consumer" sees package "svg" from bundle "svg"

  Scenario: A package seen through a chain of reexported bundles is consistent with an import that uses it
    Given there is bundle "root"
    And it exports package "p"
    And there is bundle "middle"
    And it requires bundle "root" and reexports it
    And there is bundle "top"
    And it requires bundle "middle" and reexports it
    And there is bundle "other"
    And it exports package "p" in version "2.0.0"
    And there is bundle "user"
    And it imports package "p"
    And it exports package "q" using "p"
    And there is bundle "consumer"
    And it requires bundle "top"
    And it imports package "q"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "user" is wired to bundle "root" for package "p"
    And bundle "consumer" sees package "p" from bundle "root"
