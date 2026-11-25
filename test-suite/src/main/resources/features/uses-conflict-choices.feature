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

    Scenario: An unrelated fragment in the resolve does not prevent the provider from adapting
      Given there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "host"
      And there is a fragment bundle "fragment" of bundle "host"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "consumer" is wired to bundle "middle" for package "q"

    Scenario: An import that a fragment adds to the provider adapts as well
      The import of the fragment is an import of its host, so the host can pick another provider of the package.
      Given there is bundle "middle"
      And it exports package "q" using "p"
      And there is a fragment bundle "fragment" of bundle "middle"
      And it imports package "p"
      And there is bundle "consumer"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      When I resolve bundle "consumer" and bundle "fragment"
      Then the resolution succeeds
      And bundle "middle" is wired to bundle "p-1" for package "p"
      And bundle "consumer" is wired to bundle "middle" for package "q"

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

    Scenario: Two optional imports that would see different providers of a package leave one unwired
      A bundle optionally imports a package that uses "matchers" from one provider and optionally imports "matchers" from another provider.
      Wiring both would expose the bundle to two different providers of "matchers", so one of the optional imports has to stay unwired.
      Which one is left unwired is not specified.
      Given there is bundle "junit"
      And it exports package "matchers" in version "1.3.0"
      And it exports package "rules" using "matchers"
      And there is bundle "hamcrest"
      And it exports package "matchers" in version "2.2.0"
      And there is bundle "assertj"
      And it optionally imports package "rules"
      And it optionally imports package "matchers" in version range "[2.2,3.0)"
      When I resolve bundle "assertj"
      Then the resolution succeeds
      And bundle "assertj" is resolved
      And bundle "assertj" is wired to bundle "hamcrest" for package "matchers"

    Scenario: An optional bundle that would force the shared provider to change is left out
      The resolver "must find a solution that includes at least the initial mandatory resources and should include the optional resources".
      A solution without "consumer-b" exists, so failing the resolve is not conforming.
      Given that osgi/org.osgi.service.resolver#54 is a valid interpretation of the specification
      And there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer-a" and optionally bundle "consumer-b"
      Then the resolution succeeds
      And bundle "consumer-a" is resolved
      And bundle "consumer-b" is not resolved

    Scenario: Only the optional bundle that is in the way is left out
      Only "consumer-b" is in the way, "consumer-c" is happy with either version and has to stay.
      Given that osgi/org.osgi.service.resolver#54 is a valid interpretation of the specification
      And there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      And there is bundle "consumer-c"
      And it imports package "q"
      And it imports package "p"
      When I resolve bundle "consumer-a" and optionally the bundles "consumer-b, consumer-c"
      Then the resolution succeeds
      And bundle "consumer-a" is resolved
      And bundle "consumer-b" is not resolved
      And bundle "consumer-c" is resolved
      And bundle "consumer-c" is wired to bundle "p-1" for package "p"

    Scenario: Leaving out an optional bundle takes the optional bundles that need it with it
      Leaving out "consumer-b" takes "user-of-b" with it, the unrelated optional bundle stays.
      Given that osgi/org.osgi.service.resolver#54 is a valid interpretation of the specification
      And there is bundle "middle"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "consumer-a"
      And it imports package "q"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "q"
      And it imports package "p" in version range "[2.0,3.0)"
      And it exports package "b"
      And there is bundle "user-of-b"
      And it imports package "b"
      And there is bundle "unrelated"
      When I resolve bundle "consumer-a" and optionally the bundles "consumer-b, user-of-b, unrelated"
      Then the resolution succeeds
      And bundle "consumer-a" is resolved
      And bundle "consumer-b" is not resolved
      And bundle "user-of-b" is not resolved
      And bundle "unrelated" is resolved

    Scenario: An optional bundle is left out although the conflict is two levels below it
      The provider of "p" has to change two levels down, which an optional bundle on the top level prevents.
      Given that osgi/org.osgi.service.resolver#54 is a valid interpretation of the specification
      And there is bundle "low"
      And it imports package "p"
      And it exports package "q" using "p"
      And there is bundle "high"
      And it imports package "q"
      And it exports package "r" using "q"
      And there is bundle "consumer-a"
      And it imports package "r"
      And it imports package "p" in version range "[1.0,2.0)"
      And there is bundle "consumer-b"
      And it imports package "r"
      And it imports package "p" in version range "[2.0,3.0)"
      When I resolve bundle "consumer-a" and optionally bundle "consumer-b"
      Then the resolution succeeds
      And bundle "low" is wired to bundle "p-1" for package "p"
      And bundle "consumer-a" is resolved
      And bundle "consumer-b" is not resolved

  Rule: A resource that can not be resolved does not take the resources it was tried with down
    Optional resources are independent, the failure of one is no reason to leave out another.
    A bundle that imports from two versions of a library, one of which dropped a package, can never get a consistent class space.

    Background:
      Given there is bundle "core-new"
      And it exports package "codecs" in version "3.0.0"
      And there is bundle "core-old"
      And it exports package "codecs" in version "2.0.0"
      And it exports package "codecs.old" in version "2.0.0" using "codecs"
      And there is bundle "analysis"
      And it imports package "codecs" in version range "[3.0,4.0)"
      And it exports package "analysis" in version "3.0.0" using "codecs"
      And there is bundle "legacy"
      And it imports package "codecs" in version range "[2.0,4.0)"
      And it imports package "codecs.old" in version range "[2.0,3.0)"
      And it exports package "queries" in version "2.0.0" using "codecs"
      And there is bundle "parser"
      And it imports package "queries" in version range "[2.0,3.0)"
      And it exports package "classic" in version "2.0.0" using "queries"
      And there is bundle "mixed"
      And it imports package "analysis"
      And it imports package "classic"
      And it imports package "codecs"

    Scenario: The bundles that are consistent on their own are resolved, only the inconsistent one is left out
      When I resolve all bundles as optional resources
      Then the resolution succeeds
      And bundle "legacy" is wired to bundle "core-old" for package "codecs.old"
      And bundle "legacy" is wired to bundle "core-old" for package "codecs"
      And bundle "parser" is resolved
      And bundle "analysis" is wired to bundle "core-new" for package "codecs"
      And bundle "mixed" is not resolved

    Scenario: The inconsistent bundle fails with the conflict if it is the one that is needed
      When I resolve bundle "mixed"
      Then the resolution fails because of a uses constraint violation on package "codecs" for bundle "legacy"

  Rule: A conflict is resolved by the choices it depends on, the others stay as ranked

    Scenario: A provider of a package the conflict does not involve is not replaced on the way to the solution
      Every set of choices that is consistent is a valid outcome, but a choice that does not contribute to the solution has no reason to differ from the ranking.
      Only the provider of "report" is in conflict, so "util" stays with the exporter that is ranked first.
      Given there is bundle "base-1"
      And it exports package "util" in version "1.0.0"
      And it exports package "report" in version "1.0.0"
      And there is bundle "base-2"
      And it exports package "util" in version "1.0.0"
      And there is bundle "framework"
      And it exports package "report" in version "1.0.0"
      And it imports package "util" in version range "[1.0,2.0)"
      And it exports package "build" in version "1.0.0" using "util,report"
      And there is bundle "app"
      And it imports package "build"
      And it imports package "util" in version range "[1.0,2.0)"
      And it imports package "report" in version range "[1.0,2.0)"
      When I resolve bundle "app"
      Then the resolution succeeds
      And bundle "app" is wired to bundle "framework" for package "report"
      And bundle "app" is wired to bundle "base-1" for package "util"
      And bundle "framework" is wired to bundle "base-1" for package "util"

  Rule: A package the conflict does not involve either stays with the preferred provider or moves with the others
    The specification asks for a consistent class space and for the order of preference otherwise.
    It is open whether "text", that is not part of the conflict, stays with the preferred provider while the other imports of the bundle move.
    Both outcomes are consistent, a resolver follows one of them and skips the scenario of the other one.

    Background:
      Given that osgi/org.osgi.framework#81 is a valid interpretation of the specification
      And there is bundle "stdlib-common"
      And it exports package "text" in version "1.0.0"
      And it exports package "collections" in version "1.0.0"
      And there is bundle "stdlib"
      And it exports package "text" in version "1.0.0"
      And it exports package "collections" in version "1.0.0"
      And it exports package "functions" in version "1.0.0" using "collections"
      And there is bundle "app"
      And it imports package "text" in version range "[1.0,2.0)"
      And it imports package "collections" in version range "[1.0,2.0)"
      And it imports package "functions" in version range "[1.0,2.0)"

    Scenario: Option A: an import that is not part of the conflict stays with the preferred provider
      Given the resolver leans towards Option A
      When I resolve bundle "app"
      Then the resolution succeeds
      And bundle "app" is wired to bundle "stdlib" for package "collections"
      And bundle "app" is wired to bundle "stdlib" for package "functions"
      And bundle "app" is wired to bundle "stdlib-common" for package "text"

    Scenario: Option B: an import that is not part of the conflict moves with the others
      Given the resolver leans towards Option B
      When I resolve bundle "app"
      Then the resolution succeeds
      And bundle "app" is wired to bundle "stdlib" for package "collections"
      And bundle "app" is wired to bundle "stdlib" for package "functions"
      And bundle "app" is wired to bundle "stdlib" for package "text"
