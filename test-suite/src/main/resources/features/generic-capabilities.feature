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
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.dependencies
#   https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html
Feature: Generic capabilities and requirements
  Arbitrary namespaces are resolved by matching the requirement filter against the capability attributes.

  Scenario: A requirement is wired to a matching capability
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=foo"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "my.ns"

  Scenario: A requirement without a provider fails
    Given there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns"

  Scenario: A capability in a different namespace does not match
    Given there is bundle "provider"
    And it provides capability "other.ns" with "name=foo"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns"

  Scenario: A capability with a different attribute value does not match
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=bar"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns"

  Scenario: A typed version attribute is compared as version
    Given there is bundle "provider"
    And it provides capability "my.ns" with "version:Version=1.10.0"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(&(version>=1.2)(!(version>=2.0)))"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "my.ns"

  Scenario Outline: A version range filter selects the provider
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=foo;version:Version=<provided>"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(&(name=foo)(&(version>=1.1.0)(!(version>=2.0.0))))"
    When I resolve bundle "consumer"
    Then the resolution <result>

    Examples:
      | provided | result                                                                                                           |
      | 1.1.0    | succeeds                                                                                                         |
      | 1.9.9    | succeeds                                                                                                         |
      | 1.0.0    | fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns"              |
      | 2.0.0    | fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns"              |

  Scenario: A requirement with single cardinality is wired to one provider only
    Given there is bundle "provider-1"
    And it provides capability "my.ns"
    And there is bundle "provider-2"
    And it provides capability "my.ns"
    And there is bundle "consumer"
    And it requires capability "my.ns"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 1 wire

  Scenario: A requirement with multiple cardinality is wired to all providers
    Given there is bundle "provider-1"
    And it provides capability "my.ns"
    And there is bundle "provider-2"
    And it provides capability "my.ns"
    And there is bundle "consumer"
    And it requires capability "my.ns" with "cardinality:=multiple"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 2 wires
    And bundle "consumer" is wired to bundle "provider-1" for capability in namespace "my.ns"
    And bundle "consumer" is wired to bundle "provider-2" for capability in namespace "my.ns"

  Scenario: A filter that matches every capability is wired
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=foo"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(!(never=true))"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "my.ns"

  Scenario: A missing optional requirement is ignored
    Given there is bundle "consumer"
    And it optionally requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 0 wires

  Scenario: A requirement that is not effective at resolve time is ignored
    Given there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)" and "effective:=active"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 0 wires

  Scenario: A non effective requirement does not prevent wiring the effective ones
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=foo"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    And it requires capability "other.ns" with filter "(name=bar)" and "effective:=active"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" has 1 wire

  Scenario: The mandatory directive is only evaluated in osgi.wiring namespaces
    Given there is bundle "provider"
    And it provides capability "my.ns" with "name=foo;secret=x;mandatory:=secret"
    And there is bundle "consumer"
    And it requires capability "my.ns" with filter "(name=foo)"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider" for capability in namespace "my.ns"

  Scenario: A provider that can not be resolved is skipped
    Given there is bundle "provider-broken"
    And it provides capability "my.ns" with "version:Version=2.0"
    And it imports package "missing"
    And there is bundle "provider-good"
    And it provides capability "my.ns" with "version:Version=1.0"
    And there is bundle "consumer"
    And it requires capability "my.ns"
    When I resolve bundle "consumer"
    Then the resolution succeeds
    And bundle "consumer" is wired to bundle "provider-good" for capability in namespace "my.ns"

  Scenario: Requirements can form a cycle
    Given there is bundle "a"
    And it provides capability "ns.a"
    And it requires capability "ns.b"
    And there is bundle "b"
    And it provides capability "ns.b"
    And it requires capability "ns.a"
    When I resolve bundle "a"
    Then the resolution succeeds
    And bundle "a" is wired to bundle "b" for capability in namespace "ns.b"
    And bundle "b" is wired to bundle "a" for capability in namespace "ns.a"
