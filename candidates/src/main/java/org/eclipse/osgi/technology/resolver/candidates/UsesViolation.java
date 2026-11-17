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
import java.util.Optional;

/**
 * Why a resource can not be resolved: it would see a package from different
 * providers.
 */
public class UsesViolation {

    private final String packageName;
    private final List<ResolverCapability> conflicting;
    private final Optional<ResolverRequirement> hidden;
    private final List<ResolverCapability> seen;
    private final List<ResolverCapability> implied;
    private final List<UsesChain> chains;

    /**
     * Creates a violation.
     *
     * @param packageName the name of the inconsistent package
     * @param conflicting the capabilities of the package that would have to be
     *     seen at once
     * @param hidden the import of a package whose selected export is discarded
     *     by substitution, empty for the other violations, see
     *     {@link #getHiddenImport()}
     * @param seen the capabilities the resource sees the package from, empty if
     *     the violation does not tell
     * @param implied the capabilities of the package the owners of the chains
     *     see, empty if the violation does not tell
     * @param chains why the resource sees the capabilities that use the package
     *     and expect it from {@code implied}, empty if the violation does not
     *     tell
     */
    public UsesViolation(String packageName,
            List<ResolverCapability> conflicting,
            Optional<ResolverRequirement> hidden, List<ResolverCapability> seen,
            List<ResolverCapability> implied, List<UsesChain> chains) {
        this.packageName = packageName;
        this.conflicting = Collections
                .unmodifiableList(new ArrayList<>(conflicting));
        this.hidden = hidden;
        this.seen = Collections.unmodifiableList(new ArrayList<>(seen));
        this.implied = Collections.unmodifiableList(new ArrayList<>(implied));
        this.chains = Collections.unmodifiableList(new ArrayList<>(chains));
    }

    /**
     * What the resource itself sees of the package.
     *
     * @return the capabilities, empty if the violation does not tell
     */
    public List<ResolverCapability> getSeen() {
        return seen;
    }

    /**
     * What the capabilities at the end of the {@link #getChains() chains} see of the package and therefore expect,
     * which is not what the resource sees.
     *
     * @return the capabilities, empty if the violation does not tell
     */
    public List<ResolverCapability> getImplied() {
        return implied;
    }

    /**
     * Why the resource sees the capabilities that use the package and expect it from {@link #getImplied()}.
     * The requirements on the chains are where a different choice resolves the conflict.
     *
     * @return one chain for each such capability, empty if the violation does not tell
     */
    public List<UsesChain> getChains() {
        return chains;
    }

    /**
     * The import that is wired to an export that its exporter discards by
     * substitution, empty if the violation is about a package seen from
     * different providers. Such a resource lacks a provider rather than having
     * an inconsistent class space.
     *
     * @return the import, empty for the other violations
     */
    public Optional<ResolverRequirement> getHiddenImport() {
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
        if (hidden.isPresent()) {
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
