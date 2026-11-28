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

import java.util.List;


/**
 * An exported package of a scenario bundle.
 *
 * @param atom    the name of the atom in the Alloy model
 * @param pkg     the package name
 * @param uses    the packages of the {@code uses} directive
 */
record ExportSpec(String atom, String pkg, List<String> uses) {
}
