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
 * A resolver that decides the class space conflicts the way the Apache Felix
 * resolver does, by permutating the choices that are to blame.
 */
@Export
@Version("1.0.0")
package org.eclipse.osgi.technology.resolver.permutation;

import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.versioning.Version;
