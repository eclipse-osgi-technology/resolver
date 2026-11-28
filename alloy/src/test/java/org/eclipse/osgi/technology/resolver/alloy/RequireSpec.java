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

import org.osgi.framework.VersionRange;

/**
 * A required bundle of a scenario bundle.
 *
 * @param atom     the name of the atom in the Alloy model
 * @param name     the symbolic name of the required bundle
 * @param range    the bundle version range, {@code null} matches every version
 * @param optional {@code true} if the requirement is optional
 * @param reexport {@code true} if the requirement has {@code visibility:=reexport}
 */
record RequireSpec(String atom, String name, VersionRange range, boolean optional, boolean reexport) {
}
