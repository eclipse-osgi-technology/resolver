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
import org.osgi.framework.namespace.AbstractWiringNamespace;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * A {@link ResourceBuilder} for one bundle of a scenario that knows which
 * bundle it builds. The identity, bundle and host capabilities are added when
 * it is created. Exported packages always carry the symbolic name and version
 * of this bundle, the {@link ResourceBuilder} would leave both out if they are
 * not given explicitly.
 */
public class BundleBuilder extends ResourceBuilder {

    private final BundleRef ref;

    /**
     * Creates the builder of a bundle.
     *
     * @param ref the symbolic name and version of the bundle, a missing version
     *            is the empty version
     */
    public BundleBuilder(BundleRef ref) {
        this.ref = new BundleRef(ref.name(), ref.version() == null ? Version.emptyVersion : ref.version());
        addCapability(new CapReqBuilder(IdentityNamespace.IDENTITY_NAMESPACE)
                .addAttribute(IdentityNamespace.IDENTITY_NAMESPACE, this.ref.name())
                .addAttribute(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, this.ref.version())
                .addAttribute(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE, IdentityNamespace.TYPE_BUNDLE));
        addCapability(wiring(BundleNamespace.BUNDLE_NAMESPACE));
        addCapability(wiring(HostNamespace.HOST_NAMESPACE));
    }

    /**
     * Exports a package of this bundle, with its symbolic name and version as
     * attributes.
     *
     * @param name  the package name
     * @param attrs the attributes and directives of the export
     */
    @Override
    public void addExportPackage(String name, Attrs attrs) {
        addExportPackage(name, attrs, ref.name(), ref.version());
    }

    private CapReqBuilder wiring(String namespace) {
        return new CapReqBuilder(namespace).addAttribute(namespace, ref.name())
                .addAttribute(AbstractWiringNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, ref.version());
    }
}
