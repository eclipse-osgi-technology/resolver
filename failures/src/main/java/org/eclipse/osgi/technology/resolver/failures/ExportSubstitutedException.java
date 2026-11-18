/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.failures;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * A mandatory import can only be satisfied by an export that is discarded: its exporter imports the package as
 * well and has to use the export of another provider (Core specification, "Importing and Exporting the Same
 * Package"), no other provider is left for the requirement.
 * The requirement does have a candidate, so this is not a plain {@link UnsatisfiedRequirementException}.
 */
public class ExportSubstitutedException extends UnsatisfiedRequirementException {

    private static final long serialVersionUID = 1L;

    private final transient Capability export;
    private final transient Capability substitute;

    /**
     * Creates the exception.
     *
     * @param requirement the requirement that can not be satisfied
     * @param export the export that is discarded
     * @param substitute the capability the exporter is wired to instead
     */
    public ExportSubstitutedException(Requirement requirement, Capability export, Capability substitute) {
        super(requirement, "Requirement " + requirement + " can only be satisfied by " + export
                + ", that is discarded as its exporter imports the package from " + substitute);
        this.export = export;
        this.substitute = substitute;
    }

    /**
     * The export the requirement could be wired to, it is discarded.
     *
     * @return the export
     */
    public Capability getExport() {
        return export;
    }

    /**
     * The capability the exporter is wired to instead of its own export.
     *
     * @return the substitute
     */
    public Capability getSubstitute() {
        return substitute;
    }
}
