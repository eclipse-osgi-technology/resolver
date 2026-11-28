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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import edu.mit.csail.sdg.ast.Command;

/**
 * Runs the commands of the files in {@code explore} and compares the result
 * with their {@code expect} clause: 0 means no instance (or counterexample), 1
 * means there is one.
 */
public class ExploreTest {

    private static final List<String> FILES = List.of("semantic_versions_explore", "situations");

    @TestFactory
    Stream<DynamicTest> commands() {
        return FILES.stream().flatMap(file -> {
            AlloyRunner runner = AlloyRunner.explore(file);
            return runner.commands().stream().map(c -> DynamicTest.dynamicTest(file + ": " + c.label,
                    () -> assertEquals(c.expects == 1, runner.satisfiable(c), describe(c))));
        });
    }

    private static String describe(Command c) {
        return (c.check ? "check " : "run ") + c.label + " expects " + c.expects;
    }
}
