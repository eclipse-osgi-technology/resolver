/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.diagnostics;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Implemented by a {@code ResolveContext} (or anything the resolver is able to
 * adapt the context to) that is interested in the diagnostics of a resolve
 * operation.
 * A resolver that supports diagnostics calls {@link #publishDiagnostic(Diagnostics)}
 * once per resolve operation, for successful as well as for failed operations.
 */
@ConsumerType
public interface DiagnosticsListener {

    /**
     * Called by the resolver with the diagnostics of the current resolve operation.
     *
     * @param diagnostics never {@code null}, might be empty
     */
    void publishDiagnostic(Diagnostics diagnostics);
}
