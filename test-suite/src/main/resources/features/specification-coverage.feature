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
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html#framework.module.resolvingprocess
#   https://docs.osgi.org/specification/osgi.core/8.0.0/framework.resource.html
Feature: Specification coverage
  Corners of the specification that the other features only touch indirectly.

  Rule: The bundle-version attribute of an import constrains the version of the exporting bundle

    Scenario: The bundle version of the exporter is in the range
      Given there is bundle "exporter" version 1.2.0
      And it exports package "p"
      And there is bundle "consumer"
      And it imports package "p" with 'bundle-version="[1.0,2.0)"'
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "exporter" for package "p"

    Scenario: The bundle version of the exporter is outside of the range
      Given there is bundle "exporter" version 2.1.0
      And it exports package "p"
      And there is bundle "consumer"
      And it imports package "p" with 'bundle-version="[1.0,2.0)"'
      When I resolve bundle "consumer"
      Then the resolution fails because bundle "consumer" has an unsatisfied import of package "p"

    Scenario: The bundle version selects between exporters with the same package version
      Given there is bundle "exporter" version 1.0.0
      And it exports package "p" in version "1.0.0"
      And there is bundle "exporter" version 2.0.0
      And it exports package "p" in version "1.0.0"
      And there is bundle "consumer"
      And it imports package "p" with 'bundle-version="[1.0,2.0)"'
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "exporter" version 1.0.0 for package "p"

  Rule: Requirements that are not effective at resolve time are ignored by the resolver

    Scenario: An import that is only effective at runtime does not need a provider
      Given there is bundle "consumer"
      And it imports package "p" with "effective:=active"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" has 0 wires

    Scenario: A generic requirement that is only effective at runtime does not need a provider
      Given there is bundle "consumer"
      And it requires capability "my.ns" with filter "(name=missing)" and "effective:=active"
      When I resolve bundle "consumer"
      Then the resolution succeeds

  Rule: The cardinality directive has no effect on a package or bundle requirement

    The requirement is wired to at most one capability, even if it says multiple and several capabilities match.
    Other namespaces honour the directive, see generic-capabilities.feature.

    Background:
      Given that osgi/org.osgi.framework#82 is a valid interpretation of the specification

    Scenario: A bundle is required with cardinality multiple
      Given there is bundle "lib" version 1.0.0
      And there is bundle "lib" version 2.0.0
      And there is bundle "consumer"
      And it requires bundle "lib" with multiple cardinality
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "lib" version 2.0.0
      And bundle "consumer" has 1 wire

    Scenario: A package is imported with cardinality multiple
      Given there is bundle "first"
      And it exports package "p"
      And there is bundle "second"
      And it exports package "p"
      And there is bundle "consumer"
      And it imports package "p" with multiple cardinality
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" has 1 wire

  Rule: Version ranges and filters follow their grammar

    Scenario Outline: An exclusive or inclusive bound of a version range
      Given there is bundle "exporter"
      And it exports package "p" in version "<exported>"
      And there is bundle "consumer"
      And it imports package "p" in version range "<range>"
      When I resolve bundle "consumer"
      Then the resolution <outcome>

      Examples:
        | range     | exported | outcome                                                                  |
        | (1.0,2.0) | 1.0.0    | fails because bundle "consumer" has an unsatisfied import of package "p" |
        | (1.0,2.0) | 1.0.1    | succeeds                                                                 |
        | (1.0,2.0) | 2.0.0    | fails because bundle "consumer" has an unsatisfied import of package "p" |
        | [1.0,2.0] | 2.0.0    | succeeds                                                                 |
        | [1.0,2.0] | 2.0.1    | fails because bundle "consumer" has an unsatisfied import of package "p" |
        | 1.5       | 1.4.9    | fails because bundle "consumer" has an unsatisfied import of package "p" |
        | 1.5       | 7.0.0    | succeeds                                                                 |
        | [1.0,1.0] | 1.0.0    | succeeds                                                                 |
        | [1.0,1.0] | 1.0.1    | fails because bundle "consumer" has an unsatisfied import of package "p" |

    Scenario Outline: A compound attribute filter on a capability
      Given there is bundle "provider"
      And it provides capability "my.ns" with "name=foo;kind=a"
      And there is bundle "consumer"
      And it requires capability "my.ns" with filter "<filter>"
      When I resolve bundle "consumer"
      Then the resolution <outcome>

      Examples:
        | filter                   | outcome                                                                                             |
        | (&(name=foo)(kind=a))    | succeeds                                                                                            |
        | (&(name=foo)(!(kind=a))) | fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns" |
        | (\|(name=bar)(kind=a))   | succeeds                                                                                            |
        | (&(name=f*)(kind=*))     | succeeds                                                                                            |
        | (!(name=foo))            | fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "my.ns" |

  Rule: An execution environment is a capability of the system bundle

    Scenario: A bundle requiring an execution environment is resolved if it is provided
      Given there is bundle "system.bundle"
      And it provides capability "osgi.ee" with "osgi.ee=JavaSE;version=17.0.0"
      And there is bundle "consumer"
      And it requires capability "osgi.ee" with filter "(&(osgi.ee=JavaSE)(version=17.0.0))"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "system.bundle" for capability in namespace "osgi.ee"

    Scenario: A bundle requiring an execution environment that is not provided fails
      Given there is bundle "system.bundle"
      And it provides capability "osgi.ee" with "osgi.ee=JavaSE;version=11.0.0"
      And there is bundle "consumer"
      And it requires capability "osgi.ee" with filter "(&(osgi.ee=JavaSE)(version=17.0.0))"
      When I resolve bundle "consumer"
      Then the resolution fails because bundle "consumer" has an unsatisfied requirement for capability in namespace "osgi.ee"

  Rule: A bundle that imports and exports a package keeps working without another exporter

    Scenario: The only exporter of a package imports it too
      Given there is bundle "both"
      And it exports package "p"
      And it imports package "p"
      When I resolve bundle "both"
      Then the resolution succeeds
      And bundle "both" has 0 wires

    Scenario: A bundle that imports its own export is wired to the better exporter
      Given there is bundle "both"
      And it exports package "p" in version "1.0.0"
      And it imports package "p"
      And there is bundle "better"
      And it exports package "p" in version "2.0.0"
      When I resolve bundle "both" and bundle "better"
      Then the resolution succeeds
      And bundle "both" is wired to bundle "better" for package "p"
      And bundle "both" substitutes its export of package "p"

  Rule: Fragments contribute requirements and capabilities to the host as if they were its own

    Scenario: A fragment requires a bundle for its host
      Given there is bundle "lib"
      And it exports package "p"
      And there is bundle "host"
      And there is a fragment bundle "fragment" of bundle "host"
      And it requires bundle "lib"
      When I resolve bundle "host" and bundle "fragment"
      Then the resolution succeeds
      And bundle "host" is wired to bundle "lib" for capability in namespace "osgi.wiring.bundle"
      And bundle "host" sees package "p" from bundle "lib"

    Scenario: A fragment with an unsatisfied bundle requirement is not attached
      Given there is bundle "host"
      And there is a fragment bundle "fragment" of bundle "host"
      And it requires bundle "missing"
      When I resolve bundle "host" and optionally bundle "fragment"
      Then bundle "host" is resolved
      And bundle "fragment" is not resolved

    Scenario: An export of a fragment that uses a package of the host constrains the consumers
      Given there is bundle "provider-1"
      And it exports package "p" in version "1.0.0"
      And there is bundle "provider-2"
      And it exports package "p" in version "2.0.0"
      And there is bundle "host"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is a fragment bundle "fragment" of bundle "host"
      And it exports package "q" using "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: An export of a fragment that uses a package of the host is consistent with the same provider
      Given there is bundle "provider-1"
      And it exports package "p" in version "1.0.0"
      And there is bundle "provider-2"
      And it exports package "p" in version "2.0.0"
      And there is bundle "host"
      And it imports package "p"
      And there is a fragment bundle "fragment" of bundle "host"
      And it exports package "q" using "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution succeeds
      And bundle "host" is wired to bundle "provider-1" for package "p"
      And bundle "consumer" is wired to bundle "provider-1" for package "p"

    Scenario: A package exported by a fragment is seen by the host in its own class space
      Given there is bundle "other"
      And it exports package "p"
      And there is bundle "host"
      And it exports package "q" using "p"
      And there is a fragment bundle "fragment" of bundle "host"
      And it exports package "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" from bundle "other"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: A package of a fragment is seen through Require-Bundle of the host
      Given there is bundle "other"
      And it exports package "p"
      And there is bundle "middle"
      And it imports package "p" from bundle "other"
      And it exports package "r" using "p"
      And there is bundle "host"
      And there is a fragment bundle "fragment" of bundle "host"
      And it exports package "p"
      And there is bundle "consumer"
      And it requires bundle "host"
      And it imports package "r"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: A package of a fragment is seen by the consumer that requires the host
      Given there is bundle "host"
      And there is a fragment bundle "fragment" of bundle "host"
      And it exports package "p"
      And there is bundle "consumer"
      And it requires bundle "host"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution succeeds
      And bundle "consumer" sees package "p" from bundle "host"

  Rule: Bundles that depend on each other resolve together, a cycle is no reason to fail

    Scenario: Two bundles importing from each other
      Given there is bundle "a"
      And it exports package "pa"
      And it imports package "pb"
      And there is bundle "b"
      And it exports package "pb"
      And it imports package "pa"
      When I resolve bundle "a"
      Then the resolution succeeds
      And the resolved bundles are exactly "a, b"
      And bundle "a" is wired to bundle "b" for package "pb"
      And bundle "b" is wired to bundle "a" for package "pa"

    Scenario: Three bundles forming an import cycle
      Given there is bundle "a"
      And it exports package "pa"
      And it imports package "pc"
      And there is bundle "b"
      And it exports package "pb"
      And it imports package "pa"
      And there is bundle "c"
      And it exports package "pc"
      And it imports package "pb"
      When I resolve bundle "a"
      Then the resolution succeeds
      And the resolved bundles are exactly "a, b, c"
      And bundle "a" is wired to bundle "c" for package "pc"
      And bundle "b" is wired to bundle "a" for package "pa"
      And bundle "c" is wired to bundle "b" for package "pb"

    Scenario: A cycle with one unresolvable member fails
      Given there is bundle "a"
      And it exports package "pa"
      And it imports package "pb"
      And there is bundle "b"
      And it exports package "pb"
      And it imports package "pa"
      And it imports package "missing"
      When I resolve bundle "a"
      Then the resolution fails because bundle "b" has an unsatisfied import of package "missing"

    Scenario: Bundles requiring each other
      Given there is bundle "a"
      And it requires bundle "b"
      And there is bundle "b"
      And it requires bundle "a"
      When I resolve bundle "a"
      Then the resolution succeeds
      And the resolved bundles are exactly "a, b"
      And bundle "a" is wired to bundle "b" for capability in namespace "osgi.wiring.bundle"
      And bundle "b" is wired to bundle "a" for capability in namespace "osgi.wiring.bundle"

    Scenario: Three bundles requiring each other in a cycle
      Given there is bundle "a"
      And it requires bundle "b"
      And it requires bundle "c"
      And there is bundle "b"
      And there is bundle "c"
      And it requires bundle "a"
      When I resolve bundle "a"
      Then the resolution succeeds
      And the resolved bundles are exactly "a, b, c"
      And bundle "a" has 2 wires
      And bundle "c" is wired to bundle "a" for capability in namespace "osgi.wiring.bundle"
