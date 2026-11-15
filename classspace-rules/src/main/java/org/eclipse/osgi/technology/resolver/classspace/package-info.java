/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/**
 * The rules that decide if the providers selected for a resource give it a
 * consistent class space: the {@code uses} directive ({@code UsesRule}) and the
 * substitution of exports ({@code SubstitutionRule}). A rule reads the
 * candidate graph and the index and reports the first violation it finds, it
 * never changes the graph and has no opinion about what to do with a violation.
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.classspace;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
