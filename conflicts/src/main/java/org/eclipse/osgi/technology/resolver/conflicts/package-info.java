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
 * Allows a resolver to use a pluggable strategy to resolve the class space
 * conflicts that are detected for a selection: a
 * {@code UsesConflictStrategy} works on a {@code UsesSession}, which shows the
 * candidate graph, the indices and the rule that tells if a class space is
 * consistent.
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.conflicts;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
