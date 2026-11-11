/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.testsuite;

import org.osgi.framework.Version;

/**
 * Reference to a bundle of a scenario by symbolic name and optionally by
 * version.
 *
 * @param name    the symbolic name
 * @param version the version, {@code null} matches any version
 */
public record BundleRef(String name, Version version) {

    /**
     * Creates a reference to a bundle of any version.
     *
     * @param name the symbolic name
     * @return the reference
     */
    public static BundleRef of(String name) {
        return new BundleRef(name, null);
    }

    @Override
    public String toString() {
        return version == null ? "bundle \"" + name + "\"" : "bundle \"" + name + "\" version " + version;
    }
}
