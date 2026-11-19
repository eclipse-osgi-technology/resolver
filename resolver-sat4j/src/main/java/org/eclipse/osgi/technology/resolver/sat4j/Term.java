/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.sat4j;

import java.util.Arrays;

/**
 * A conjunction of literals, one way a defined variable becomes true (a wire, for a fragment also its attachment).
 */
final class Term {

    private final int[] literals;

    Term(int... literals) {
        this.literals = literals;
    }

    /**
     * The literals that still have to hold once the constants are folded away.
     *
     * @return the literals, empty if the term always holds or {@code null} if it never does
     */
    int[] open() {
        int[] kept = new int[literals.length];
        int size = 0;
        for (int literal : literals) {
            if (literal == PseudoBooleanProblem.FALSE || literal == -PseudoBooleanProblem.TRUE) {
                return null;
            }
            if (literal != PseudoBooleanProblem.TRUE) {
                kept[size++] = literal;
            }
        }
        return Arrays.copyOf(kept, size);
    }
}
