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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sat4j.core.Vec;
import org.sat4j.core.VecInt;
import org.sat4j.pb.IPBSolver;
import org.sat4j.pb.ObjectiveFunction;
import org.sat4j.pb.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.TimeoutException;

/**
 * A pseudo boolean optimisation problem: variables, clauses, at most one constraints and the lexicographic objective
 * the encoding of the class space rule is stated in. It knows nothing about resources, it only collects what the
 * encoding adds and hands it to Sat4j.
 * <p>
 * Literals are the variable numbers, negative for the negation. {@link #TRUE} and {@link #FALSE} are the constants:
 * a clause that contains {@code TRUE} is always satisfied and dropped, {@code FALSE} (and the negation of
 * {@code TRUE}) is removed from a clause.
 */
class PseudoBooleanProblem {

    static final int TRUE = Integer.MAX_VALUE;
    static final int FALSE = 0;

    int variables;
    boolean contradiction;
    final List<int[]> clauses = new ArrayList<>();
    final List<int[]> atMostOne = new ArrayList<>();
    final Set<List<Integer>> seenClauses = new HashSet<>();

    /**
     * The weight of a variable in the objective of the lowest priority, per variable.
     */
    final Map<Integer, Long> preference = new LinkedHashMap<>();
    /**
     * Variables that are true if a resource is given up.
     */
    final List<Integer> givenUp = new ArrayList<>();
    /**
     * Variables that are true if an optional resource is not resolved.
     */
    final List<Integer> missedOptional = new ArrayList<>();

    int newVariable() {
        return ++variables;
    }

    /**
     * Constants are folded: a satisfied clause is dropped, false literals are removed, an empty clause is a
     * contradiction.
     */
    void clause(int... literals) {
        int[] kept = new int[literals.length];
        int size = 0;
        for (int literal : literals) {
            if (literal == TRUE) {
                return;
            }
            if (literal != FALSE && literal != -TRUE) {
                kept[size++] = literal;
            }
        }
        if (size == 0) {
            contradiction = true;
            return;
        }
        clauses.add(Arrays.copyOf(kept, size));
    }

    void clauseOnce(int... literals) {
        List<Integer> key = new ArrayList<>(literals.length);
        for (int literal : literals) {
            key.add(literal);
        }
        if (seenClauses.add(key)) {
            clause(literals);
        }
    }

    /**
     * Solves the problem with the objective.
     *
     * @param timeoutSeconds the time the solver may take, the best solution found so far is used after it
     * @return the truth value per variable (index 0 is unused) or {@code null} if there is no solution (or none was
     *         found in time)
     */
    boolean[] solve(long timeoutSeconds) {
        if (contradiction) {
            return null;
        }
        int[] model = runSolver(timeoutSeconds);
        if (model == null) {
            return null;
        }
        boolean[] truth = new boolean[variables + 1];
        for (int literal : model) {
            if (literal > 0 && literal < truth.length) {
                truth[literal] = true;
            }
        }
        return truth;
    }

    /**
     * The objective, lexicographic by scaling: optional resources that are not resolved outweigh everything, then
     * resources given up, then unwired optional requirements and candidates ranked lower.
     */
    private ObjectiveFunction objective() {
        IVecInt literals = new VecInt();
        IVec<BigInteger> coefficients = new Vec<>();
        BigInteger sum = BigInteger.ZERO;
        for (Map.Entry<Integer, Long> entry : preference.entrySet()) {
            BigInteger weight = BigInteger.valueOf(entry.getValue());
            literals.push(entry.getKey());
            coefficients.push(weight);
            sum = sum.add(weight);
        }
        BigInteger given = sum.add(BigInteger.ONE);
        for (Integer variable : givenUp) {
            literals.push(variable);
            coefficients.push(given);
            sum = sum.add(given);
        }
        BigInteger optional = sum.add(BigInteger.ONE);
        for (Integer variable : missedOptional) {
            literals.push(variable);
            coefficients.push(optional);
        }
        return new ObjectiveFunction(literals, coefficients);
    }

    private int[] runSolver(long timeoutSeconds) {
        IPBSolver solver = SolverFactory.newDefaultOptimizer();
        solver.setTimeout((int) timeoutSeconds);
        solver.newVar(variables);
        solver.setExpectedNumberOfClauses(clauses.size() + atMostOne.size());
        try {
            for (int[] clause : clauses) {
                solver.addClause(new VecInt(clause));
            }
            for (int[] literals : atMostOne) {
                solver.addAtMost(new VecInt(literals), 1);
            }
            solver.setObjectiveFunction(objective());
        } catch (ContradictionException e) {
            return null;
        }
        try {
            boolean satisfiable = solver.isSatisfiable();
            return satisfiable ? solver.model() : null;
        } catch (TimeoutException e) {
            try {
                return solver.model();
            } catch (RuntimeException noModel) {
                return null;
            }
        }
    }

    static int not(int literal) {
        return literal == TRUE ? FALSE : literal == FALSE ? TRUE : -literal;
    }

    static int[] with(int[] literals, int... more) {
        int[] result = Arrays.copyOf(literals, literals.length + more.length);
        System.arraycopy(more, 0, result, literals.length, more.length);
        return result;
    }
}
