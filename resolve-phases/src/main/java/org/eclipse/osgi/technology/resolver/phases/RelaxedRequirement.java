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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A requirement with another filter, see {@link HostIdentityFilter}.
 */
class RelaxedRequirement implements Requirement {

    private final Requirement wrapped;
    private final Map<String, String> directives;

    RelaxedRequirement(Requirement wrapped, String filter) {
        this.wrapped = wrapped;
        Map<String, String> copy = new HashMap<>(wrapped.getDirectives());
        copy.put("filter", filter);
        this.directives = Collections.unmodifiableMap(copy);
    }

    @Override
    public String getNamespace() {
        return wrapped.getNamespace();
    }

    @Override
    public Map<String, String> getDirectives() {
        return directives;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return wrapped.getAttributes();
    }

    @Override
    public Resource getResource() {
        return wrapped.getResource();
    }

    @Override
    public String toString() {
        return wrapped + " without the identity of the bundle";
    }
}
