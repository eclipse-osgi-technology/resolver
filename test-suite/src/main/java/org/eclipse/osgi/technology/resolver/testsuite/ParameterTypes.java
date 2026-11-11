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

import io.cucumber.java.ParameterType;

/**
 * Custom parameter types of the scenario language.
 */
public class ParameterTypes {

    /**
     * A bundle as written in a scenario: {@code bundle "A"} or
     * {@code bundle "A" version 1.2.0}.
     *
     * @param name    the symbolic name
     * @param version the version, {@code null} if the scenario does not name one
     * @return the reference to the bundle
     */
    @ParameterType("bundle \"([^\"]+)\"(?: version (\\d+(?:\\.\\d+){0,2}(?:\\.[\\w-]+)?))?")
    public BundleRef bundle(String name, String version) {
        return new BundleRef(name, version == null ? null : Version.parseVersion(version));
    }
}
