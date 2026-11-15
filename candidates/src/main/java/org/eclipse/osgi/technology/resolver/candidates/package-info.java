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
 * The graph of candidates a resolver decides on. Every {@code Resource},
 * {@code Capability} and {@code Requirement} is wrapped into a counterpart that
 * still implements the wrapped interface but additionally caches decoded
 * information (for example {@code uses}) and carries the state of the
 * resolution: the candidates of a requirement and why each was selected or
 * rejected.
 * <p>
 * Thread safety: the graph is not thread safe. Its structure is set up first
 * and does not change afterwards, only the state does (the resolve state of a
 * resource, the state of a candidate). The state is kept in plain fields, so a
 * change must not overlap with any other access to the same object. Callers
 * that work concurrently have to separate reading and changing into phases,
 * with a happens-before edge between them, for example by waiting for all tasks
 * of a phase before the next one starts.
 * </p>
 * <p>
 * Wrappers have identity semantics, they are looked up through the original
 * object.
 * </p>
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.candidates;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
