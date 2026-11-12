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
Feature: Resolving uses conflicts
  A conflict is only a failure if no combination of choices avoids it.

  Rule: Resolving uses conflicts by choosing differently
    A conflict is only a failure if no combination of choices avoids it.
    The consumer of a package can pick another provider, a provider can pick another dependency, and a change of one choice can affect other bundles.

    Background:
      Given there is bundle "p-1"
      And it exports package "p" in version "1.0.0"
      And there is bundle "p-2"
      And it exports package "p" in version "2.0.0"

    Scenario: The consumer picks another provider of the package it needs, the provider is pinned
      Given there is bundle "middle-old"
      And it imports package "p" in version range "[1.0,2.0)"
      And it exports package "q" in version "2.0.0" using "p"
      And there is bundle "middle-new"
      And it imports package "p" in version range "[2.0,3.0)"
      And it exports package "q" in version "1.0.0" using "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "middle-new" for package "q"
      And bundle "consumer" is wired to bundle "p-2" for package "p"
      And bundle "middle-old" is not resolved

    Scenario: The provider adapts and the consumer keeps its preferred provider
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" in version "2.0.0" using "p"
      And there is bundle "middle-spare"
      And it imports package "p"
      And it exports package "q" in version "1.0.0" using "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "middle" for package "q"
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "middle-spare" is not resolved

    Scenario: Adapting a shared provider for one consumer is fine if the other consumer agrees
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,1.5)"
      When I resolve bundle "consumer-a" and bundle "consumer-b"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "consumer-a" is wired to bundle "middle" for package "q"
      And bundle "consumer-b" is wired to bundle "middle" for package "q"

    Scenario: Adapting a shared provider for one consumer breaks another, which then takes another provider
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" in version "2.0.0" using "p"
      And there is bundle "middle-spare"
      And it imports package "p" in version range "[2.0,3.0)"
      And it exports package "q" in version "1.0.0" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer-a" and bundle "consumer-b"
      Then the resolution succeeds
      And bundle "consumer-a" is wired to bundle "middle" for package "q"
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "consumer-b" is wired to bundle "middle-spare" for package "q"
      And bundle "middle-spare" is wired to bundle "p-2" for package "p"

    Scenario: A shared provider without an alternative can not serve consumers that need different providers
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer-a" and bundle "consumer-b"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer-a"

    Scenario: An optional import of a needed bundle is left unwired to keep the other mandatory bundle consistent
      The bundle "consumer-b" needs "q" from "middle", which then has to use "p-2".
      So "consumer-a" can not see "q" and keep "p-1", but its import of "q" is optional and can stay unwired.
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it optionally imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer-a" and bundle "consumer-b"
      Then the resolution succeeds
      And bundle "consumer-a" is wired to bundle "p-1" for package "p"
      And bundle "consumer-b" is wired to bundle "middle" for package "q"
      And bundle "consumer-a" is not wired to bundle "middle"

    Scenario: A bundle that is only pulled in by an optional import is left out for a mandatory one
      Nobody asked for "extra", it would only be pulled in by the optional import of "consumer".
      Given there is bundle "extra"
      And it imports package "p" in version range "[2.0,3.0)"
      And it exports package "x" using "p"
      And there is bundle "consumer"
      And it optionally imports package "x"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "p-1" for package "p"
      And bundle "consumer" is not wired to bundle "extra"

    Scenario: The provider has to adapt through two levels
      Given there is bundle "low"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "high"
      And it imports package "q"
      And it exports package "r" using "q"
      And there is bundle "consumer"
      And it imports package "r"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "low" is wired to bundle "p-1" for package "p"
      And bundle "high" is wired to bundle "low" for package "q"

    Scenario: The conflict is found through a second path and the provider there can adapt
      Given there is bundle "left"
      And it imports package "p"
      And it exports package "l" using "p"
      And there is bundle "right"
      And it imports package "p" in version range "[1.0,2.0)"
      And it exports package "r" using "p"
      And there is bundle "consumer"
      And it imports package "l"
      And it imports package "r"
      And it imports package "p"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "right" is wired to bundle "p-1" for package "p"
      And bundle "left" is wired to bundle "p-1" for package "p"

    Scenario: The provider can not adapt because it has an own constraint, the consumer has no other provider
      Given there is bundle "middle"
      And it imports package "p" in version range "[2.0,3.0)"
      And it exports package "q" using "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution fails because of a uses constraint violation on package "p" for bundle "consumer"

    Scenario: Two different used packages are decided independently
      Given there is bundle "r-1"
      And it exports package "r" in version "1.0.0"
      And there is bundle "r-2"
      And it exports package "r" in version "2.0.0"
      And there is bundle "middle"
      And it imports package "p"
      And it imports package "r"
      And it exports package "q" using "p,r"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And it imports package "r" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "middle" is wired to bundle "r-1" for package "r"

    Scenario: The packages a bundle imports from one provider move to another provider together
      Given there is bundle "lib-1"
      And it exports package "a" in version "1.0.0"
      And it exports package "b" in version "1.0.0"
      And it exports package "c" in version "1.0.0"
      And there is bundle "lib-2"
      And it exports package "a" in version "2.0.0"
      And it exports package "b" in version "2.0.0"
      And it exports package "c" in version "2.0.0"
      And there is bundle "middle"
      And it imports package "a"
      And it imports package "b"
      And it imports package "c"
      And it exports package "q" using "a,b,c"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "a" in version range "[1.0,2.0)"
      And it imports package "b" in version range "[1.0,2.0)"
      And it imports package "c" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "lib-1" for package "a"
      And bundle "middle" is wired to bundle "lib-1" for package "b"
      And bundle "middle" is wired to bundle "lib-1" for package "c"
      And bundle "lib-2" is not resolved

    Scenario: An optional import that would see an exported package from another provider stays unwired
      The bundle "embedding" exports package "p" itself.
      Wiring its optional import of "q" would make it see "p" from "api" as well, which is inconsistent with its own export.
      An optional import may stay unwired instead (3.6.5 Class Space Consistency).
      Given there is bundle "api"
      And it exports package "p"
      And it exports package "q" using "p"
      And there is bundle "embedding"
      And it exports package "p"
      And it optionally imports package "q"
      When I resolve bundle "api" and bundle "embedding"
      Then the resolution succeeds
      And bundle "embedding" is resolved
      And bundle "embedding" is not wired to bundle "api"

    Scenario: The provider of a generic capability adapts to the package its consumer imports
      What a capability of any namespace uses is part of the class space of its requirer.
      Given there is bundle "impl"
      And it imports package "p"
      And it provides capability "my.impl" with "name=impl;uses:=p"
      And there is bundle "consumer"
      And it requires capability "my.impl" with filter "(name=impl)"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer"
      Then the resolution succeeds
      And bundle "consumer" is wired to bundle "impl" for capability in namespace "my.impl"
      And bundle "impl" is wired to bundle "p-1" for package "p"
      And bundle "consumer" is wired to bundle "p-1" for package "p"
