/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.failures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.ResolutionException;

/**
 * No consistent class space exists: a resource would see a package from different providers
 * because of the {@code uses} directive of an exported package.
 */
public class UsesConstraintViolationException extends ResolutionException {

    private static final long serialVersionUID = 1L;

    private final String packageName;
    private final transient Resource resource;
    private final transient Collection<Capability> conflictingCapabilities;

    public UsesConstraintViolationException(String packageName, Resource resource,
            Collection<Capability> conflictingCapabilities, Collection<Requirement> unresolved) {
        super("Uses constraint violation on package " + packageName + " for " + resource, null, unresolved);
        this.packageName = packageName;
        this.resource = resource;
        this.conflictingCapabilities = Collections.unmodifiableCollection(new ArrayList<Capability>(conflictingCapabilities));
    }

    /** The package that would be seen from different providers. */
    public String getPackageName() {
        return packageName;
    }

    /** The resource that would see the package inconsistently. */
    public Resource getResource() {
        return resource;
    }

    /** The different exports of the package that would have to be seen at once. */
    public Collection<Capability> getConflictingCapabilities() {
        return conflictingCapabilities;
    }
}
