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
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.importpackage
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.exportpackage
Feature: Import-Package
  A bundle can only be resolved if every mandatory package import is wired to a matching export.
  An import matches an export of the same package name whose version is inside the requested range.

  Scenario: An import is wired to the matching export
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"
    And bundle "exporter" is resolved

  Scenario: A bundle can import several packages from different bundles
    Given there is bundle "exporter-a"
    And it exports package "a"
    And there is bundle "exporter-b"
    And it exports package "b"
    And there is bundle "importer"
    And it imports package "a"
    And it imports package "b"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter-a" for package "a"
    And bundle "importer" is wired to bundle "exporter-b" for package "b"
    And bundle "importer" has 2 wires

  Scenario: Several importers are wired to the same exporter
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer-1"
    And it imports package "foo"
    And there is bundle "importer-2"
    And it imports package "foo"
    When I resolve bundle "importer-1" and bundle "importer-2"
    Then the resolution succeeds
    And bundle "importer-1" is wired to bundle "exporter" for package "foo"
    And bundle "importer-2" is wired to bundle "exporter" for package "foo"

  Scenario: An import without any exporter can not be resolved
    Given there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer"
    Then the resolution fails because bundle "importer" has an unsatisfied import of package "foo"

  Scenario: A package of a different name does not match
    Given there is bundle "exporter"
    And it exports package "foo.bar"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer"
    Then the resolution fails because bundle "importer" has an unsatisfied import of package "foo"

  Scenario: An export is not wired to a bundle that does not import it
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "bystander"
    When I resolve bundle "bystander" and bundle "exporter"
    Then the resolution succeeds
    And bundle "bystander" is not wired to bundle "exporter"

  Scenario: An import without version range matches any version
    Given there is bundle "exporter"
    And it exports package "foo" in version "3.5.0"
    And there is bundle "importer"
    And it imports package "foo"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"

  Scenario: An export with a version below the requested range does not match
    Given there is bundle "exporter"
    And it exports package "foo" in version "1.0.0"
    And there is bundle "importer"
    And it imports package "foo" in version range "[2.0,3.0)"
    When I resolve bundle "importer"
    Then the resolution fails because bundle "importer" has an unsatisfied import of package "foo"

  Scenario: An export with a version inside the requested range matches
    Given there is bundle "exporter"
    And it exports package "foo" in version "2.1.0"
    And there is bundle "importer"
    And it imports package "foo" in version range "[2.0,3.0)"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"

  Scenario: The upper bound of an exclusive range does not match
    Given there is bundle "exporter"
    And it exports package "foo" in version "2.0.0"
    And there is bundle "importer"
    And it imports package "foo" in version range "[1.0,2.0)"
    When I resolve bundle "importer"
    Then the resolution fails because bundle "importer" has an unsatisfied import of package "foo"

  Scenario: The upper bound of an inclusive range matches
    Given there is bundle "exporter"
    And it exports package "foo" in version "2.0.0"
    And there is bundle "importer"
    And it imports package "foo" in version range "[1.0,2.0]"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"

  Scenario: A plain version in an import means that version or higher
    Given there is bundle "exporter"
    And it exports package "foo" in version "5.0.0"
    And there is bundle "importer"
    And it imports package "foo" in version range "2.0"
    When I resolve bundle "importer"
    Then the resolution succeeds
    And bundle "importer" is wired to bundle "exporter" for package "foo"

  Scenario: An export without version has version 0.0.0
    Given there is bundle "exporter"
    And it exports package "foo"
    And there is bundle "importer"
    And it imports package "foo" in version range "[1.0,2.0)"
    When I resolve bundle "importer"
    Then the resolution fails because bundle "importer" has an unsatisfied import of package "foo"
