/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.permutation;

/**
 * Why a permutation was created, the queue hands out the permutations by this order: the ones that follow from a
 * class space conflict first, then the ones that backtrack an import decision, the ones for substitution last.
 */
enum PermutationType {
    USES, IMPORT, SUBSTITUTE
}
