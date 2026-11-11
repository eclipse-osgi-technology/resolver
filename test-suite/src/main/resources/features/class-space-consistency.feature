#
# Copyright (c) 2026 Contributors to the Eclipse Foundation.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Specification (constraint solving, the "uses" directive):
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.constraint.solving
Feature: Class space consistency (uses constraint)
  An exported package can declare that it "uses" other packages.
  A bundle that sees the exported package must then see the used packages from the same exporter as the exporting bundle does.
  The resolver has to pick wires so that this holds, even if that means not taking the preferred (highest version) provider.

  Rule: A provider of a used package is chosen so that every consumer sees the same one

    Background:
      Given there is bundle "provider-old"
      And it exports package "p" in version "1.0.0"
      And there is bundle "provider-new"
      And it exports package "p" in version "2.0.0"
      And there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"

    Scenario: Without other constraints the preferred provider is used consistently
      Given there is bundle "consumer"
      And it imports package "q"
      And it imports package "p"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "provider-new" for package "p"
      And bundle "middle" is wired to bundle "provider-new" for package "p"

    Scenario: The consumer gets the same provider of the used package as the exporter of the other package
      Given there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "middle" for package "q"
      And bundle "consumer" is wired to bundle "provider-old" for package "p"
      And bundle "middle" is wired to bundle "provider-old" for package "p"
      And bundle "provider-new" is not resolved

    Scenario: The exporter of the other package adapts to the consumer, not the other way around
      Given there is bundle "consumer"
      And it imports package "p" in version range "[2.0,3.0)"
      And it imports package "q"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "provider-new" for package "p"
      And bundle "consumer" is wired to bundle "provider-new" for package "p"

    Scenario: Conflicting views on a used package can not be resolved
      Given there is bundle "middle-pinned"
      And it imports package "p" in version range "[1.0,2.0)"
      And it exports package "q2" using "p"
      And there is bundle "consumer"
      And it imports package "q2"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: The used package is consistent when exporter and used package come from one bundle
      Given there is bundle "self-contained"
      And it exports package "a"
      And it exports package "b" using "a"
      And there is bundle "consumer"
      And it imports package "a"
      And it imports package "b"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "self-contained" for package "a"
      And bundle "consumer" is wired to bundle "self-contained" for package "b"

    Scenario: Uses constraints are transitive
      Given there is bundle "q-exporter"
      And it imports package "p" in version range "[1.0,2.0)"
      And it exports package "q2" using "p"
      And there is bundle "r-exporter"
      And it imports package "q2"
      And it exports package "r" using "q2"
      And there is bundle "consumer"
      And it imports package "r"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: Different bundles reached through Require-Bundle must agree on a used package
      Given there is bundle "a"
      And it requires bundle "b"
      And it requires bundle "c"
      And there is bundle "b"
      And it exports package "p1"
      And there is bundle "c"
      And it requires bundle "d"
      And it exports package "p2" using "p1"
      And there is bundle "d"
      And it exports package "p1"
      When I resolve bundle "a"
      Then the resolution fails because of a uses constraint violation on package "p1" for bundle "a"

    Scenario: A conflict reached indirectly through Require-Bundle also fails
      Given there is bundle "x"
      And it requires bundle "a"
      And there is bundle "a"
      And it requires bundle "b"
      And it requires bundle "c"
      And there is bundle "b"
      And it exports package "p1"
      And there is bundle "c"
      And it requires bundle "d"
      And it exports package "p2" using "p1"
      And there is bundle "d"
      And it exports package "p1"
      When I resolve bundle "x"
      Then the resolution fails because of a uses constraint violation on package "p1" for bundle "a"

  Rule: Class space consistency with a single provider of the used package
    If only one bundle exports a used package there is no alternative to choose from.
    Either every bundle sees that provider and the class space is consistent, or a requirement is not satisfied.
    A uses constraint violation needs two different providers.

    Background:
      Given there is bundle "provider"
      And it exports package "p" in version "1.0.0"
      And there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"

    Scenario: Everybody sees the only provider of the used package
      Given there is bundle "consumer"
      And it imports package "q"
      And it imports package "p"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "provider" for package "p"
      And bundle "middle" is wired to bundle "provider" for package "p"

    Scenario: A consumer that does not import the used package is consistent
      Given there is bundle "consumer"
      And it imports package "q"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "middle" for package "q"

    Scenario: A single provider that does not match the consumer is an unsatisfied import, not a uses violation
      Given there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer"
      Then the resolution fails because bundle "consumer" has an unsatisfied import of package "p"

    Scenario: The used package is not provided by anybody
      Given there is bundle "lonely"
      And it exports package "r" using "unknown"
      And there is bundle "consumer"
      And it imports package "r"
      And it imports package "unknown"
      When I resolve bundle "consumer"
      Then the resolution fails because bundle "consumer" has an unsatisfied import of package "unknown"

    Scenario: Packages implied by two chains do not conflict as long as the consumer does not see the used package
      Implied packages are no imports.
      Two chains that imply different providers only conflict once the bundle imports or exports the package too.

      Given there is bundle "api-1"
      And it exports package "a" in version "1.0.0"
      And there is bundle "api-2"
      And it exports package "a" in version "2.0.0"
      And there is bundle "b"
      And it imports package "a" in version range "[1.0,2.0)"
      And it exports package "p1" using "a"
      And there is bundle "c"
      And it imports package "a" in version range "[2.0,3.0)"
      And it exports package "p2" using "a"
      And there is bundle "consumer-a"
      And it imports package "p1"
      And it imports package "p2"
      When I resolve bundle "consumer-a"
      Then the resolution succeeds
      And bundle "b" is wired to bundle "api-1" for package "a"
      And bundle "c" is wired to bundle "api-2" for package "a"

    Scenario: The same chains conflict as soon as the consumer imports the used package
      Given there is bundle "api-1"
      And it exports package "a" in version "1.0.0"
      And there is bundle "api-2"
      And it exports package "a" in version "2.0.0"
      And there is bundle "b"
      And it imports package "a" in version range "[1.0,2.0)"
      And it exports package "p1" using "a"
      And there is bundle "c"
      And it imports package "a" in version range "[2.0,3.0)"
      And it exports package "p2" using "a"
      And there is bundle "consumer-a"
      And it imports package "p1"
      And it imports package "p2"
      And it imports package "a"
      When I resolve bundle "consumer-a"
      Then the resolution fails because of a uses constraint violation on package "a" for bundle "consumer-a"

    Scenario: Packages implied through Require-Bundle by two chains do not conflict either
      Given there is bundle "api-1"
      And it exports package "a" in version "1.0.0"
      And there is bundle "api-2"
      And it exports package "a" in version "2.0.0"
      And there is bundle "b"
      And it requires bundle "api-1"
      And it exports package "p1" using "a"
      And there is bundle "c"
      And it requires bundle "api-2"
      And it exports package "p2" using "a"
      And there is bundle "consumer-a"
      And it imports package "p1"
      And it imports package "p2"
      When I resolve bundle "consumer-a"
      Then the resolution succeeds
      And bundle "consumer-a" is wired to bundle "b" for package "p1"
      And bundle "consumer-a" is wired to bundle "c" for package "p2"

    Scenario: Every requirement has exactly one candidate and the class space is still inconsistent
      Given there is bundle "api-1"
      And it exports package "a" in version "1.0.0"
      And there is bundle "api-2"
      And it exports package "a" in version "2.0.0"
      And there is bundle "api-3"
      And it exports package "a" in version "3.0.0"
      And there is bundle "b"
      And it imports package "a" in version range "[1.0,2.0)"
      And it exports package "p1" using "a"
      And there is bundle "c"
      And it imports package "a" in version range "[2.0,3.0)"
      And it exports package "p2" using "a"
      And there is bundle "consumer-a"
      And it imports package "p1"
      And it imports package "p2"
      And it imports package "a" in version range "[3.0,4.0)"
      When I resolve bundle "consumer-a"
      Then the resolution fails because of a uses constraint violation on package "a" for bundle "consumer-a"
