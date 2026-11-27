/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

import io.cucumber.java.en.Given;

/**
 * The steps that select an interpretation of the specification. The model has
 * one reading only, so scenarios that depend on an interpretation are skipped.
 */
public class SpecificationSteps {

    private final AlloyWorld world;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public SpecificationSteps(AlloyWorld world) {
        this.world = world;
    }

    /**
     * The step {@code that {word} is a valid interpretation of the specification}.
     *
     * @param reference the reference of the interpretation
     */
    @Given("that {word} is a valid interpretation of the specification")
    public void validInterpretation(String reference) {
        world.unsupported("interpretation " + reference);
    }

    /**
     * The step {@code the resolver leans towards Option {word}}.
     *
     * @param option the name of the option
     */
    @Given("the resolver leans towards Option {word}")
    public void option(String option) {
        world.unsupported("interpretation option " + option);
    }
}
