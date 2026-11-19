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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the {@link PackageView} of the resources of one resolve operation.
 * The view of a resource depends on the selected candidates of the resources it sees, so a change
 * of a candidate or of the attached fragments of any resource of the scope makes all views stale.
 * Candidates and resources report such a change automatically, whoever changes the graph in another way
 * has to call {@link #invalidate()}, which must not run while views are requested.
 */
public final class PackageViewCache {

    private final Map<ResolverResource, PackageView> views = new ConcurrentHashMap<>();
    private final List<Runnable> undo = new ArrayList<>();
    private int checkpoints;

    /**
     * Marks the current state of the graph of this scope, to get back to it with {@link Checkpoint#rollback()}.
     * Every change made to candidates and resources while a checkpoint is open is remembered, so rolling back
     * costs what was changed, not the size of the graph.
     *
     * @return the checkpoint, to be closed
     */
    public Checkpoint checkpoint() {
        checkpoints++;
        return new Checkpoint(this, undo.size());
    }

    boolean isRecording() {
        return checkpoints > 0;
    }

    void record(Runnable reverse) {
        undo.add(reverse);
    }

    void rollback(int mark) {
        for (int i = undo.size() - 1; i >= mark; i--) {
            undo.remove(i).run();
        }
        invalidate();
    }

    void release() {
        if (--checkpoints == 0) {
            undo.clear();
        }
    }

    /**
     * The view of a resource on the current selection of candidates, it is computed on first use.
     *
     * @param resource the resource
     * @return the view, only valid until the next {@link #invalidate()}
     */
    public PackageView getView(ResolverResource resource) {
        return views.computeIfAbsent(resource, r -> new PackageView(r, this));
    }

    /**
     * Drops the views of all resources of this scope.
     */
    public void invalidate() {
        views.clear();
    }
}
