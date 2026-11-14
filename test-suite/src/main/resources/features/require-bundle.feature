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
Feature: Require-Bundle
  A bundle can require another bundle by its symbolic name and optionally a version range.
  A bundle can only be resolved if every mandatory required bundle is resolvable.

  Scenario: A required bundle is wired
    Given there is bundle "provider"
    And there is bundle "consumer"
    And it requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "osgi.wiring.bundle"
    And the resolved bundles are exactly "consumer, provider"

  Scenario: A missing required bundle fails the resolution
    Given there is bundle "consumer"
    And it requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "consumer" has an unsatisfied requirement of bundle "provider"

  Scenario: A required bundle in the version range is wired
    Given there is bundle "provider" version 1.5.0
    And there is bundle "consumer"
    And it requires bundle "provider" in version range "[1.0,2.0)"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" version 1.5.0

  Scenario: A bundle version outside the range does not match
    Given there is bundle "provider" version 2.0.0
    And there is bundle "consumer"
    And it requires bundle "provider" in version range "[1.0,2.0)"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "consumer" has an unsatisfied requirement of bundle "provider"

  Scenario: A required bundle that can not be resolved fails the requirer
    Given there is bundle "provider"
    And it imports package "missing"
    And there is bundle "consumer"
    And it requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "provider" has an unsatisfied import of package "missing"

  Scenario: Required bundles are resolved transitively
    Given there is bundle "a"
    And it requires bundle "b"
    And there is bundle "b"
    And it requires bundle "c"
    And there is bundle "c"
    When I resolve bundle "a"
    Then the resolution succeeds
    And the resolved bundles are exactly "a, b, c"
    And bundle "a" is wired to bundle "b" for capability in namespace "osgi.wiring.bundle"
    And bundle "b" is wired to bundle "c" for capability in namespace "osgi.wiring.bundle"

  Scenario: A reexported required bundle does not make the requirer wire to it directly
    Given there is bundle "a"
    And it requires bundle "b"
    And there is bundle "b"
    And it requires bundle "c" and reexports it
    And there is bundle "c"
    And it exports package "pc"
    When I resolve bundle "a"
    Then the resolution succeeds
    And the resolved bundles are exactly "a, b, c"
    And bundle "a" is wired to bundle "b" for capability in namespace "osgi.wiring.bundle"
    And bundle "a" is not wired to bundle "c"

  Scenario: A missing optional required bundle does not prevent resolving
    Given there is bundle "consumer"
    And it optionally requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 0 wires

  Scenario: An available optional required bundle is wired
    Given there is bundle "provider"
    And there is bundle "consumer"
    And it optionally requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "osgi.wiring.bundle"

  Scenario: An optional required bundle is not wired if it can not be resolved
    Given there is bundle "provider"
    And it imports package "missing"
    And there is bundle "consumer"
    And it optionally requires bundle "provider"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 0 wires
    And bundle "provider" is not resolved

  Scenario: An optional requirement chain is only wired as far as resolvable
    Given there is bundle "res2"
    And it requires bundle "res5"
    And it requires bundle "res4"
    And it optionally requires bundle "res3"
    And there is bundle "res3"
    And it requires bundle "res2"
    And it requires capability "foo" with filter "(foo=bar)"
    And there is bundle "res4"
    And it requires bundle "res5"
    And there is bundle "res5"
    When I resolve bundle "res2"
    Then the resolution succeeds
    And the resolved bundles are exactly "res2, res4, res5"
    And bundle "res2" has 2 wires
    And bundle "res4" has 1 wire
    And bundle "res5" has 0 wires

  Rule: Alternatives of a requirement are not duplicated in the result

    Scenario: Two requirements for the same bundle pull in one version only
      Given there is bundle "lib" version 1.0.0
      And there is bundle "lib" version 2.0.0
      And there is bundle "first"
      And it requires bundle "lib"
      And there is bundle "second"
      And it requires bundle "lib" in version range "[1.0,3.0)"
      When I resolve bundle "first" and bundle "second"
      Then the resolution succeeds
      And bundle "first" is wired to bundle "lib" version 2.0.0
      And bundle "second" is wired to bundle "lib" version 2.0.0
      And the resolved bundles are exactly "first, second, lib"

    Scenario: A package import is wired to the version of the bundle that is required already
      Given there is bundle "lib" version 1.0.0
      And it exports package "p" in version "1.0.0"
      And there is bundle "lib" version 2.0.0
      And it exports package "p" in version "1.0.0"
      And there is bundle "first"
      And it requires bundle "lib" in version range "[1.0,2.0)"
      And there is bundle "second"
      And it imports package "p"
      When I resolve bundle "first" and bundle "second"
      Then the resolution succeeds
      And bundle "first" is wired to bundle "lib" version 1.0.0
      And bundle "second" is wired to bundle "lib" version 1.0.0 for package "p"
      And the resolved bundles are exactly "first, second, lib"

  Rule: A dependency without a range resolves to the highest available version
    A bundle that stands for a group of bundles, a feature, requires the bundles of its content and the features it depends on.

    Background:
      Given there is bundle "dep" version 1.0.0
      And there is bundle "dep" version 2.0.0

    Scenario: The highest version of a feature dependency is chosen
      Given there is bundle "f1"
      And it requires bundle "dep"
      When I resolve bundle "f1"
      Then the resolution succeeds
      And bundle "f1" is wired to bundle "dep" version 2.0.0

    Scenario: A version range restricts the chosen feature dependency
      Given there is bundle "f1"
      And it requires bundle "dep" in version range "[1.0,2.0)"
      When I resolve bundle "f1"
      Then the resolution succeeds
      And bundle "f1" is wired to bundle "dep" version 1.0.0

  Rule: Ranges along a chain of features select one consistent set of versions

    Background:
      Given there is bundle "lib" version 3.1.4
      And there is bundle "lib-web" version 3.1.4
      And it requires bundle "lib" in version range "[3.1.4,3.2)"
      And there is bundle "lib" version 3.2.14
      And there is bundle "lib-web" version 3.2.14
      And it requires bundle "lib" in version range "[3.2.14,3.3)"
      And there is bundle "lib" version 4.0.7
      And there is bundle "lib-web" version 4.0.7
      And it requires bundle "lib" in version range "[4.0.7,4.1)"
      And there is bundle "lib-dm" version 1.2.1
      And it requires bundle "lib" in version range "[2.5.6,4)"
      And there is bundle "lib-dm-web" version 1.2.1
      And it requires bundle "lib-dm"
      And it requires bundle "lib-web" in version range "[2.5.6,4)"

    Scenario: The upper bounds of two features lead to the highest matching pair
      When I resolve bundle "lib-dm-web"
      Then the resolution succeeds
      And bundle "lib-dm-web" is wired to bundle "lib-web" version 3.2.14
      And bundle "lib-web" version 3.2.14 is wired to bundle "lib" version 3.2.14
      And bundle "lib-dm" is wired to bundle "lib" version 3.2.14

  Rule: Optional content is wired when it is available

    Scenario: An optional feature is resolved when it is part of the request
      Given there is bundle "f2"
      And there is bundle "f1"
      And it optionally requires bundle "f2"
      When I resolve bundle "f1" and optionally bundle "f2"
      Then the resolution succeeds
      And bundle "f1" is wired to bundle "f2"

    Scenario: An optional feature that is available is wired
      Given there is bundle "f2"
      And there is bundle "f1"
      And it optionally requires bundle "f2"
      When I resolve bundle "f1"
      Then the resolution succeeds
      And bundle "f1" is wired to bundle "f2"
