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
 * An imported package of a scenario bundle.
 *
 * @param atom     the name of the atom in the Alloy model
 * @param pkg      the package name
 * @param range    the version range, {@code null} matches every version
 * @param optional {@code true} if the import is optional
 */
record ImportSpec(String atom, String pkg, VersionRange range, boolean optional) {
}
