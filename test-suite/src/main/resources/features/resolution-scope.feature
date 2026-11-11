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
#   https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html
#   (mandatory and optional resources of a ResolveContext)
Feature: Mandatory and optional resources
  Only the mandatory resources must be resolved, otherwise the resolution fails.
  Optional resources are resolved on a best effort basis, they never make a resolution fail.
  Resources that are only needed by others are part of the result, unrelated available resources are not.

  Scenario: A bundle without requirements resolves on its own
    Given there is bundle "standalone"
    When I resolve bundle "standalone"
    Then the resolution succeeds
    And the resolved bundles are exactly "standalone"
    And bundle "standalone" has 0 wires

  Scenario: Unrelated available bundles are not resolved
    Given there is bundle "unrelated"
    And it exports package "other"
    And there is bundle "standalone"
    When I resolve bundle "standalone"
    Then the resolved bundles are exactly "standalone"

  Scenario: Providers needed by a mandatory bundle are part of the result
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer"
    Then the resolved bundles are exactly "importer, exporter"

  Scenario: One unresolvable mandatory bundle fails the whole resolution
    Given there is bundle "good"
    And there is bundle "bad"
    And it imports package "missing"
    When I resolve bundle "good" and bundle "bad"
    Then the resolution fails because bundle "bad" has an unsatisfied import of package "missing"

  Scenario: Mandatory bundles that depend on each other are resolved together
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer" and bundle "exporter"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"

  Scenario: The same mandatory bundle given twice is resolved once
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer" and bundle "importer"
    Then the resolution succeeds
    And the resolved bundles are exactly "importer, exporter"
    And bundle "importer" has 1 wire

  Scenario: An unresolvable optional bundle is ignored
    Given there is bundle "standalone"
    And there is bundle "optional"
    And it imports package "missing"
    When I resolve bundle "standalone" and optionally bundle "optional"
    Then the resolution succeeds
    And the resolved bundles are exactly "standalone"

  Scenario: A resolvable optional bundle is resolved
    Given there is bundle "standalone"
    And there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "optional"
    And it imports package "foo"
    When I resolve bundle "standalone" and optionally bundle "optional"
    Then the resolution succeeds
    And the resolved bundles are exactly "standalone, optional, exporter"
    And bundle "optional" is wired to bundle "exporter" for package "foo"

  Scenario: Resolving nothing succeeds with an empty wiring
    When I resolve nothing
    Then the resolution succeeds
    And the resulting wiring is empty
