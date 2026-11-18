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

import org.osgi.resource.Resource;

/**
 * A uses constraint violation that is caused by a fragment that can not be left
 * out, because it was asked for explicitly. The fragment is to blame, a resolve
 * without it would not fail for this reason.
 */
public class FragmentConflictException extends UsesConstraintViolationException {

    private static final long serialVersionUID = 1L;

    private final transient Resource fragment;
    private final transient Resource host;

    /**
     * Creates the exception from the violation it explains.
     *
     * @param violation the uses constraint violation
     * @param fragment the fragment that is to blame
     * @param host the host the fragment is attached to
     */
    public FragmentConflictException(UsesConstraintViolationException violation, Resource fragment, Resource host) {
        super(violation.getPackageName(), violation.getResource(), violation.getConflictingCapabilities(),
                violation.getUnresolvedRequirements());
        this.fragment = fragment;
        this.host = host;
    }

    /**
     * The fragment that causes the conflict.
     *
     * @return the fragment
     */
    public Resource getFragment() {
        return fragment;
    }

    /**
     * The host the fragment makes inconsistent.
     *
     * @return the host
     */
    public Resource getHost() {
        return host;
    }
}
