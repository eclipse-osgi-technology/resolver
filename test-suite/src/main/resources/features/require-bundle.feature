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
