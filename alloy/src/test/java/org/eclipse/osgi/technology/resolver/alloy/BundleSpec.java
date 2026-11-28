/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Version;

import org.eclipse.osgi.technology.resolver.testsuite.BundleBuilder;

/**
 * A bundle of a scenario as it is written to the Alloy model. The
 * {@link BundleBuilder} describes the same bundle as a resource, the
 * requirements are matched against the capabilities with it.
 */
class BundleSpec {

    final String name;
    final Version version;
    final BundleBuilder builder;
    final String atom;
    final List<ExportSpec> exports = new ArrayList<>();
    final List<ImportSpec> imports = new ArrayList<>();
    final List<RequireSpec> requires = new ArrayList<>();
    final List<CapabilitySpec> provides = new ArrayList<>();
    final List<NeedSpec> needs = new ArrayList<>();

    BundleSpec(String name, Version version, BundleBuilder builder, String atom) {
        this.name = name;
        this.version = version;
        this.builder = builder;
        this.atom = atom;
    }
}
