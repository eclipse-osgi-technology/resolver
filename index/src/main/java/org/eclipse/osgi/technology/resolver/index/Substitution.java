/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.index;

import java.util.Collections;
import java.util.List;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * An export of a resource that the same resource imports as well: the framework
 * may discard the export if the import is wired to another resource (Core
 * specification 3.8.1, "Importing and Exporting the Same Package"). It is the
 * static part of that decision: who would be affected if the export was
 * discarded is known up front.
 */
public final class Substitution {

    private final Capability export;
    private final Requirement overlappingImport;
    private final List<Requirement> demand;
    private final Requirement pinnedBy;
    private final boolean keepable;

    Substitution(Capability export, Requirement overlappingImport, List<Requirement> demand, Requirement pinnedBy,
            boolean keepable) {
        this.export = export;
        this.overlappingImport = overlappingImport;
        this.demand = Collections.unmodifiableList(demand);
        this.pinnedBy = pinnedBy;
        this.keepable = keepable;
    }

    /**
     * The export that is kept if the import is wired to it and discarded if the
     * import is wired to anything else.
     *
     * @return the export
     */
    public Capability getExport() {
        return export;
    }

    /**
     * The import of the same package in the resource that exports it.
     *
     * @return the import
     */
    public Requirement getOverlappingImport() {
        return overlappingImport;
    }

    /**
     * The requirements of other resources that the export can satisfy, whoever
     * is wired to the export loses its provider if the export is discarded.
     *
     * @return the requirements
     */
    public List<Requirement> getDemand() {
        return demand;
    }

    /**
     * A requirement of a resource that is certain to be resolved and has
     * nothing else to be wired to but the export, discarding the export would
     * make that resource unresolvable. {@code null} if there is no such
     * requirement.
     *
     * @return the requirement that needs the export or {@code null}
     */
    public Requirement getPinnedBy() {
        return pinnedBy;
    }

    /**
     * Tells if the export can be kept at all.
     *
     * @return false if the export does not match the import that decides about
     * it, the export can only be kept by dropping the import
     */
    public boolean isKeepable() {
        return keepable;
    }

    /**
     * Tells if a resource that is certain to be resolved needs the export.
     *
     * @return true if {@link #getPinnedBy()} is not {@code null}
     */
    public boolean isPinned() {
        return pinnedBy != null;
    }

    @Override
    public String toString() {
        return "substitution of " + export + " by " + overlappingImport;
    }
}
