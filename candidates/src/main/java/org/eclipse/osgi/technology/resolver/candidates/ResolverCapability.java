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
import java.util.Map;

import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.HostedCapability;

/**
 * A {@link Capability} of a {@link ResolverResource} that caches what has to be
 * decoded from it.
 */
public class ResolverCapability implements Capability {

    private final ResolverResource owner;
    private final Capability wrapped;
    private final ResolverResource declaring;
    private volatile List<String> uses;

    /**
     * Wraps a capability that is provided by the resource that declares it.
     *
     * @param owner the resource that provides the capability
     * @param wrapped the capability to wrap
     */
    ResolverCapability(ResolverResource owner, Capability wrapped) {
        this(owner, wrapped, owner);
    }

    /**
     * A capability of a fragment provided by the host {@code owner},
     * {@code declaring} is the fragment.
     *
     * @param owner the resource that provides the capability
     * @param wrapped the capability to wrap
     * @param declaring the fragment that declares the capability
     */
    ResolverCapability(ResolverResource owner, Capability wrapped, ResolverResource declaring) {
        this.owner = owner;
        this.wrapped = wrapped;
        this.declaring = declaring;
    }

    /**
     * The capability as declared by its resource, differs from
     * {@link #unwrap()} for hosted capabilities.
     *
     * @return the declared capability
     */
    public Capability getDeclared() {
        return wrapped instanceof HostedCapability ? ((HostedCapability) wrapped).getDeclaredCapability() : wrapped;
    }

    /**
     * The resource that declares the capability, differs from
     * {@link #getOwner()} for capabilities of fragments.
     *
     * @return the declaring resource
     */
    public ResolverResource getDeclaringResource() {
        return declaring;
    }

    /**
     * The wrapped capability.
     *
     * @return the wrapped capability
     */
    public Capability unwrap() {
        return wrapped;
    }

    /**
     * The resource that provides the capability.
     *
     * @return the providing resource
     */
    public ResolverResource getOwner() {
        return owner;
    }

    /**
     * The packages named in the {@code uses} directive, decoded once.
     *
     * @return the package names, empty if there is no directive
     */
    public List<String> getUses() {
        List<String> result = uses;
        if (result == null) {
            result = new ArrayList<>();
            String directive = wrapped.getDirectives().get(PackageNamespace.CAPABILITY_USES_DIRECTIVE);
            if (directive != null) {
                for (String name : directive.split(",")) {
                    if (!name.trim().isEmpty()) {
                        result.add(name.trim());
                    }
                }
            }
            uses = result = Collections.unmodifiableList(result);
        }
        return result;
    }

    @Override
    public String getNamespace() {
        return wrapped.getNamespace();
    }

    @Override
    public Map<String, String> getDirectives() {
        return wrapped.getDirectives();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return wrapped.getAttributes();
    }

    @Override
    public Resource getResource() {
        return owner;
    }

    @Override
    public String toString() {
        Object name = wrapped.getAttributes().get(wrapped.getNamespace());
        return wrapped.getNamespace() + (name == null ? "" : "=" + name);
    }
}
