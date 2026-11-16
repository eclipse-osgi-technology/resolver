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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.technology.resolver.candidates.ResolverResource;
import org.osgi.resource.Resource;

/**
 * The resources of one resolve operation in the order they were discovered, and
 * which of them the resolve context asked for. It is what the phases of a
 * resolve hand over to each other, the state of a resolve lives in the
 * candidate graph of its resources.
 */
public final class ResolverGraph {

    private final Map<Resource, ResolverResource> resources = new LinkedHashMap<>();
    private final List<ResolverResource> mandatory = new ArrayList<>();
    private final List<ResolverResource> optional = new ArrayList<>();

    /**
     * All resources of the graph.
     *
     * @return the resources in the order they were discovered, already resolved
     *       ones included
     */
    public Collection<ResolverResource> getResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    /**
     * The resources by the resource they wrap.
     *
     * @return the resources in the order they were discovered
     */
    public Map<Resource, ResolverResource> getResourceMap() {
        return Collections.unmodifiableMap(resources);
    }

    /**
     * The resources the resolve context asked for explicitly.
     *
     * @return the resources that have to resolve
     */
    public List<ResolverResource> getMandatory() {
        return Collections.unmodifiableList(mandatory);
    }

    /**
     * The resources the resolve context asked for optionally.
     *
     * @return the resources that resolve if they can
     */
    public List<ResolverResource> getOptional() {
        return Collections.unmodifiableList(optional);
    }

    ResolverResource get(Resource resource) {
        return resources.get(resource);
    }

    void add(ResolverResource resource) {
        resources.put(resource.unwrap(), resource);
    }

    void addMandatory(ResolverResource resource) {
        mandatory.add(resource);
    }

    void addOptional(ResolverResource resource) {
        optional.add(resource);
    }
}
