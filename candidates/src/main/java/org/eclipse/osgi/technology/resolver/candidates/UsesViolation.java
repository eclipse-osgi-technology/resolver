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
import java.util.Collections;
import java.util.List;

/**
 * Why a resource can not be resolved: it would see a package from different
 * providers.
 */
public class UsesViolation {

    private final String packageName;
    private final List<ResolverCapability> conflicting;
    private final ResolverRequirement hidden;

    /**
     * Creates a violation about a package that is seen from different
     * providers.
     *
     * @param packageName the name of the inconsistent package
     * @param conflicting the capabilities of the package that would have to be
     *     seen at once
     */
    public UsesViolation(String packageName, List<ResolverCapability> conflicting) {
        this(packageName, conflicting, null);
    }

    /**
     * Creates a violation about an import whose selected export is discarded by
     * substitution.
     *
     * @param packageName the name of the inconsistent package
     * @param conflicting the capabilities of the package that would have to be
     *     seen at once
     * @param hidden the import of a package whose selected export is discarded
     *     by substitution, see {@link #getHiddenImport()}
     */
    public UsesViolation(String packageName, List<ResolverCapability> conflicting, ResolverRequirement hidden) {
        this.packageName = packageName;
        this.conflicting = Collections.unmodifiableList(new ArrayList<>(conflicting));
        this.hidden = hidden;
    }

    /**
     * The import that is wired to an export that its exporter discards by
     * substitution, {@code null} if the violation is about a package seen from
     * different providers. Such a resource lacks a provider rather than having
     * an inconsistent class space.
     *
     * @return the import or {@code null}
     */
    public ResolverRequirement getHiddenImport() {
        return hidden;
    }

    /**
     * The name of the package that is inconsistent.
     *
     * @return the package name
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * The different exports of the package that would have to be seen at once.
     *
     * @return the conflicting capabilities
     */
    public List<ResolverCapability> getConflicting() {
        return conflicting;
    }

    @Override
    public String toString() {
        if (hidden != null) {
            return "the export of package " + packageName + " of " + conflicting.get(0).getDeclaringResource().getName()
                    + " is discarded, it imports the package from " + conflicting.get(1).getOwner().getName();
        }
        StringBuilder providers = new StringBuilder();
        for (ResolverCapability capability : conflicting) {
            if (providers.length() > 0) {
                providers.append(", ");
            }
            providers.append(capability.getDeclaringResource().getName());
        }
        return "uses constraint violation on package " + packageName + ", it would see the package from different providers: "
                + providers;
    }
}
