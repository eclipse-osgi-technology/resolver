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

import java.util.Map;

import org.osgi.framework.Version;
import org.osgi.framework.namespace.AbstractWiringNamespace;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * A {@link ResourceBuilder} for one bundle of a scenario that knows which
 * bundle it builds. The identity capability and, for a bundle, its bundle and host
 * capabilities are added when it is created. Exported packages always carry the symbolic name and version
 * of this bundle, the {@link ResourceBuilder} would leave both out if they are
 * not given explicitly.
 */
public class BundleBuilder extends ResourceBuilder {

    private final BundleRef ref;

    private BundleBuilder(BundleRef ref, String type) {
        this.ref = new BundleRef(ref.name(), ref.version() == null ? Version.emptyVersion : ref.version());
        addCapability(new CapReqBuilder(IdentityNamespace.IDENTITY_NAMESPACE)
                .addAttribute(IdentityNamespace.IDENTITY_NAMESPACE, this.ref.name())
                .addAttribute(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, this.ref.version())
                .addAttribute(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE, type));
    }

    /**
     * Creates the builder of a bundle.
     *
     * @param ref        the symbolic name and version of the bundle, a missing
     *                   version is the empty version
     * @param directives the directives of its Bundle-SymbolicName clause, e.g.
     *                   {@code fragment-attachment:=never}, they are also visible
     *                   on its bundle and host capability
     * @return the builder
     */
    public static BundleBuilder bundle(BundleRef ref, Attrs directives) {
        BundleBuilder builder = new BundleBuilder(ref, IdentityNamespace.TYPE_BUNDLE);
        builder.addCapability(builder.wiring(BundleNamespace.BUNDLE_NAMESPACE, directives));
        // a host that never takes fragments has no host capability
        if (!HostNamespace.FRAGMENT_ATTACHMENT_NEVER
                .equals(directives.get(HostNamespace.CAPABILITY_FRAGMENT_ATTACHMENT_DIRECTIVE + ":"))) {
            builder.addCapability(builder.wiring(HostNamespace.HOST_NAMESPACE, directives));
        }
        return builder;
    }

    /**
     * Creates the builder of a fragment.
     *
     * @param ref        the symbolic name and version of the fragment, a missing
     *                   version is the empty version
     * @param host       the symbolic name of the host
     * @param attributes the attributes of its Fragment-Host clause, e.g. the
     *                   {@code bundle-version} range
     * @return the builder
     */
    public static BundleBuilder fragment(BundleRef ref, String host, Attrs attributes) {
        BundleBuilder builder = new BundleBuilder(ref, IdentityNamespace.TYPE_FRAGMENT);
        builder.addFragmentHost(host, attributes);
        return builder;
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

    /**
     * Adds a dynamic import of a package, which is only wired on request.
     *
     * @param name the package name
     */
    public void addDynamicImportPackage(String name) {
        addRequirement(new CapReqBuilder(PackageNamespace.PACKAGE_NAMESPACE)
                .addDirective("filter", "(" + PackageNamespace.PACKAGE_NAMESPACE + "=" + name + ")")
                .addDirective("resolution", "dynamic").buildSyntheticRequirement());
    }

    /**
     * Adds a requirement of a namespace that has no filter and therefore matches
     * every capability of that namespace.
     *
     * @param namespace  the namespace
     * @param directives the directives of the requirement, e.g.
     *                   {@code cardinality=multiple}
     */
    public void addRequireAnyCapability(String namespace, Map<String, String> directives) {
        CapReqBuilder builder = new CapReqBuilder(namespace);
        directives.forEach(builder::addDirective);
        addRequirement(builder.buildSyntheticRequirement());
    }

    private CapReqBuilder wiring(String namespace, Attrs directives) {
        CapReqBuilder capability = new CapReqBuilder(namespace).addAttribute(namespace, ref.name())
                .addAttribute(AbstractWiringNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, ref.version());
        capability.addAttributesOrDirectives(directives);
        return capability;
    }
}
