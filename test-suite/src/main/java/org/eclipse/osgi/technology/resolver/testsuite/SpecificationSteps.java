/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.testsuite;

import static org.assertj.core.api.Assumptions.assumeThat;

import io.cucumber.java.en.Given;

/**
 * Cucumber glue steps that tie a scenario to an interpretation of the
 * specification that is proposed to the specification repository.
 */
public class SpecificationSteps {

    private final TestWorld world;

    /**
     * Creates the steps, called by the dependency injection of Cucumber.
     *
     * @param world the state of the scenario
     */
    public SpecificationSteps(TestWorld world) {
        this.world = world;
    }

    /**
     * Skips the scenario unless the resolver follows the interpretation of the
     * specification that is proposed by the reference.
     *
     * @param reference the GitHub reference of the proposal,
     *                  {@code <owner>/<repository>#<number>}
     */
    @Given("that {word} is a valid interpretation of the specification")
    public void validInterpretation(String reference) {
        assumeThat(world.engine().assumeValid(reference)).as("the resolver follows the interpretation of " + reference)
                .isNotEqualTo(Interpretation.DENY);
        world.interpretation(reference);
    }

    /**
     * Skips the scenario unless the resolver produces the given outcome of the
     * proposal that was stated before.
     *
     * @param option the name of the option, for example {@code A}
     */
    @Given("the resolver leans towards Option {word}")
    public void option(String option) {
        assumeOption(Interpretation.valueOf("OPTION_" + option));
    }

    private void assumeOption(Interpretation option) {
        String reference = world.interpretation();
        assumeThat(reference).as("a step that states the interpretation comes first").isNotNull();
        Interpretation answer = world.engine().assumeValid(reference);
        if (answer == Interpretation.ACCEPT) {
            throw new AssertionError(
                    "Engine has to either choose option A or B for " + reference + " but answered ACCEPT");
        }
        assumeThat(answer).as("the option the resolver leans towards").isEqualTo(option);
    }
}
