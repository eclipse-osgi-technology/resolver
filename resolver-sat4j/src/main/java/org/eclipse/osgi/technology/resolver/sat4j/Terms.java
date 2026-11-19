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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The terms that make the defined variables of one kind (imported, bundle view, direct view) true.
 */
final class Terms {

    private final Map<Integer, List<Term>> terms = new LinkedHashMap<>();

    /**
     * Starts the definition of a variable, one without terms is never true.
     */
    void define(int variable) {
        terms.put(variable, new ArrayList<>());
    }

    /**
     * Adds one way the defined variable becomes true: all the literals hold.
     */
    void add(int variable, int... literals) {
        terms.get(variable).add(new Term(literals));
    }

    /**
     * Makes every defined variable true only if one of its terms is, otherwise the solver could set it wherever that
     * is convenient.
     */
    void close(PseudoBooleanProblem problem) {
        for (Map.Entry<Integer, List<Term>> entry : terms.entrySet()) {
            List<Integer> any = new ArrayList<>();
            any.add(-entry.getKey());
            boolean always = false;
            for (Term term : entry.getValue()) {
                int[] literals = term.open();
                if (literals == null) {
                    continue;
                }
                if (literals.length == 0) {
                    always = true;
                    break;
                }
                if (literals.length == 1) {
                    any.add(literals[0]);
                } else {
                    int conjunction = problem.newVariable();
                    for (int literal : literals) {
                        problem.clause(-conjunction, literal);
                    }
                    any.add(conjunction);
                }
            }
            if (!always) {
                problem.clause(any.stream().mapToInt(Integer::intValue).toArray());
            }
        }
    }
}
