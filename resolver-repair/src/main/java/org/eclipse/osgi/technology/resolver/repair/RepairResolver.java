/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.repair;

import org.eclipse.osgi.technology.resolver.conflicts.UsesConflictStrategy;
import org.eclipse.osgi.technology.resolver.phases.PhasedResolver;
import org.osgi.service.resolver.ResolveContext;

/**
 * A resolver that decides the open class space conflicts with a {@link RepairSearch}.
 * The resolver holds no state, every resolve creates its own search, so it can be used by several threads.
 */
public class RepairResolver extends PhasedResolver {

    @Override
    protected UsesConflictStrategy createStrategy(ResolveContext context) {
        return new RepairSearch();
    }
}
