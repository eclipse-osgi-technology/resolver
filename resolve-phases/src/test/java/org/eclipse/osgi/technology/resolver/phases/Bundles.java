/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.phases;

import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Resource;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

/**
 * Bundles and fragments for the tests, described by what a resolve looks at:
 * the identity, the host, the imported and the exported packages.
 */
final class Bundles {

    private Bundles() {
    }

    /**
     * A bundle that can host fragments.
     */
    static ResourceBuilder bundle(String name) {
        ResourceBuilder builder = identity(name, "1.0.0", IdentityNamespace.TYPE_BUNDLE);
        builder.addCapability(new CapReqBuilder(HostNamespace.HOST_NAMESPACE)
                .addAttribute(HostNamespace.HOST_NAMESPACE, name)
                .addAttribute(HostNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, new aQute.bnd.version.Version("1.0.0")));
        return builder;
    }

    /**
     * A fragment of the host with the given name and version.
     */
    static ResourceBuilder fragment(String name, String version, String host) {
        ResourceBuilder builder = identity(name, version, IdentityNamespace.TYPE_FRAGMENT);
        builder.addRequirement(new CapReqBuilder(HostNamespace.HOST_NAMESPACE)
                .addDirective("filter", "(" + HostNamespace.HOST_NAMESPACE + "=" + host + ")"));
        return builder;
    }

    /**
     * The package with the given version, it uses the other packages.
     */
    static Attrs export(String version, String... uses) {
        Attrs attrs = Attrs.create("version", version);
        if (uses.length > 0) {
            attrs.put("uses:", String.join(",", uses));
        }
        return attrs;
    }

    /**
     * A package import within the version range.
     */
    static Attrs importing(String range) {
        return Attrs.create("version", range);
    }

    /**
     * An import that does not have to be wired.
     */
    static Attrs optional(String range) {
        Attrs attrs = importing(range);
        attrs.put("resolution:", "optional");
        return attrs;
    }

    static Resource build(ResourceBuilder builder) {
        return builder.build();
    }

    private static ResourceBuilder identity(String name, String version, String type) {
        ResourceBuilder builder = new ResourceBuilder();
        builder.addCapability(new CapReqBuilder(IdentityNamespace.IDENTITY_NAMESPACE)
                .addAttribute(IdentityNamespace.IDENTITY_NAMESPACE, name)
                .addAttribute(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, new aQute.bnd.version.Version(version))
                .addAttribute(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE, type));
        return builder;
    }
}
