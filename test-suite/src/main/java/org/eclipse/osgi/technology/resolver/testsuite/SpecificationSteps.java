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
                .isTrue();
    }
}
