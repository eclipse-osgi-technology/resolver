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
 * Specific subclasses of {@link org.osgi.service.resolver.ResolutionException} for the expected reasons a resolve fails.
 * A resolver throws a plain {@code ResolutionException} only for situations it does not foresee, every expected failure
 * has a subclass that carries the details so a caller does not have to parse a message.
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.failures;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
