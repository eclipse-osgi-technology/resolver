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

/**
 * A requirement of a scenario bundle in a namespace without dedicated rules.
 *
 * @param atom     the name of the atom in the Alloy model
 * @param ns       the namespace
 * @param optional {@code true} if the requirement is optional
 * @param multiple {@code true} if the requirement has {@code cardinality:=multiple}
 */
record NeedSpec(String atom, String ns, boolean optional, boolean multiple) {
}
