/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.sat4j;

import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.phases.PhasedResolver;
import org.osgi.service.resolver.ResolveContext;

/**
 * A resolver that hands the class space conflicts the phases detect to a pseudo
 * boolean solver, see {@link Sat4jStrategy}.
 */
public class Sat4jResolver extends PhasedResolver {

    @Override
    protected UsesConflictStrategy createStrategy(ResolveContext context) {
        return new Sat4jStrategy();
    }
}
