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
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.fragmentbundles
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.fragmenthost
Feature: Fragment bundles
  A fragment is attached to a host bundle and can not be resolved without it.
  Capabilities of a fragment are provided through the host.

  Scenario: A host resolves without fragments
    Given there is bundle "host"
    When I resolve bundle "host"
    Then the resolution succeeds
    And the resolved bundles are exactly "host"

  Scenario: A fragment without a host can not be resolved
    Given there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "fragment" has no host "host"

  Scenario: A fragment is wired to its host
    Given there is bundle "host"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment" and bundle "host"
    Then the resolution succeeds
    And bundle "fragment" is wired to bundle "host"

  Scenario: Resolving a fragment pulls in its host
    Given there is bundle "host"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution succeeds
    And the resolved bundles are exactly "fragment, host"

  Scenario: A fragment can not be attached to another fragment
    Given there is a fragment bundle "host-fragment" of bundle "real-host"
    And there is a fragment bundle "fragment" of bundle "host-fragment"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "fragment" has no host "host-fragment"

  Scenario: Two fragments can be attached to the same host
    Given there is bundle "host"
    And there is a fragment bundle "fragment-1" of bundle "host"
    And there is a fragment bundle "fragment-2" of bundle "host"
    When I resolve bundle "fragment-1" and bundle "fragment-2"
    Then the resolution succeeds
    And the resolved bundles are exactly "host, fragment-1, fragment-2"
    And bundle "fragment-1" is wired to bundle "host"
    And bundle "fragment-2" is wired to bundle "host"

  Scenario: A fragment host in the version range matches
    Given there is bundle "host" version 1.5.0
    And there is a fragment bundle "fragment" of bundle "host" in version range "[1.0,2.0)"
    When I resolve bundle "fragment"
    Then the resolution succeeds
    And bundle "fragment" is wired to bundle "host" version 1.5.0

  Scenario: A host outside the version range does not match
    Given there is bundle "host" version 2.0.0
    And there is a fragment bundle "fragment" of bundle "host" in version range "[1.0,2.0)"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "fragment" has no host "host"

  Scenario: A host that always accepts fragments gets the fragment
    Given there is bundle "host" with fragment attachment policy "always"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution succeeds
    And bundle "fragment" is wired to bundle "host"

  Scenario: A host that accepts fragments at resolve time gets the fragment
    Given there is bundle "host" with fragment attachment policy "resolve-time"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution succeeds
    And bundle "fragment" is wired to bundle "host"

  Scenario: A host that never accepts fragments rejects the fragment
    Given there is bundle "host" with fragment attachment policy "never"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "fragment" has no host "host"

  Scenario: A package exported by a fragment is provided by the host
    Given there is bundle "host"
    And there is a fragment bundle "fragment" of bundle "host"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer" and bundle "fragment"
    Then the resolution succeeds
    And the resolved bundles are exactly "importer, fragment, host"
    And bundle "importer" is wired to bundle "host" for package "foo"
    And bundle "fragment" is wired to bundle "host"

  Scenario: A package import of a fragment is wired for the host
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "host"
    And there is a fragment bundle "fragment" of bundle "host"
    And it imports package "foo"
    When I resolve bundle "fragment"
    Then the resolution succeeds
    And bundle "host" is wired to bundle "exporter" for package "foo"

  Scenario: A fragment with an unsatisfied import can not be resolved
    Given there is bundle "host"
    And there is a fragment bundle "fragment" of bundle "host"
    And it imports package "missing"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "fragment" has an unsatisfied import of package "missing"

  Scenario: A host that can not be resolved fails the fragment
    Given there is bundle "host"
    And it imports package "missing"
    And there is a fragment bundle "fragment" of bundle "host"
    When I resolve bundle "fragment"
    Then the resolution fails because bundle "host" has an unsatisfied import of package "missing"
