/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.candidates;

/**
 * A state of the graph of a resolve operation that can be returned to, see {@link PackageViewCache#checkpoint()}.
 * Checkpoints nest, the one that is closed last has to be closed first.
 * Closing without a rollback keeps the changes made since.
 */
public final class Checkpoint implements AutoCloseable {

    private final PackageViewCache scope;
    private final int mark;
    private boolean open = true;

    Checkpoint(PackageViewCache scope, int mark) {
        this.scope = scope;
        this.mark = mark;
    }

    /**
     * Undoes every change to candidates and resources since the checkpoint was taken, it stays usable.
     */
    public void rollback() {
        scope.rollback(mark);
    }

    @Override
    public void close() {
        if (open) {
            open = false;
            scope.release();
        }
    }
}
