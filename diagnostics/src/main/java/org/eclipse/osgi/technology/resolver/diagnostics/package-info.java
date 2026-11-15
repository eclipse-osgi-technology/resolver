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
 * Optional diagnostics for resolve operations.
 * A {@link org.osgi.service.resolver.ResolveContext} that implements
 * {@link org.eclipse.osgi.technology.resolver.diagnostics.DiagnosticsListener} receives the diagnostics of a resolve
 * operation, regardless of its outcome.
 * Resolvers that do not know about this package simply never call it.
 * The API only depends on the {@code org.osgi.resource} types, so it can be used with any resolver.
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.diagnostics;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
