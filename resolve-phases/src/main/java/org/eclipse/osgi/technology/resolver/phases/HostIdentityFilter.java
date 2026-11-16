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
import java.util.Hashtable;
import java.util.Map;

import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

/**
 * The identity of the exporting bundle (<code>bundle-symbolic-name</code> and
 * <code>bundle-version</code>) of a capability of a fragment is the one of its
 * host (Core specification, Export-Package), while the resolve context matches
 * the capability as declared by the fragment. Requirements that ask for the
 * identity are therefore relaxed when the context is asked, and matched again
 * against the hosted capability.
 */
class HostIdentityFilter {

    private static final String SYMBOLIC_NAME = "bundle-symbolic-name";
    private static final String VERSION = "bundle-version";

    private HostIdentityFilter() {
    }

    static boolean isIdentityRequested(String filter) {
        return filter != null && (filter.contains(SYMBOLIC_NAME) || filter.contains(VERSION));
    }

    /**
     * The filter of a requirement without the terms for the identity of the
     * bundle: whatever the original filter matches is matched by it as well.
     */
    static String relax(String filter, String namespace) {
        int[] position = { 0 };
        String relaxed = relax(filter, position);
        return relaxed == null ? "(" + namespace + "=*)" : relaxed;
    }

    /**
     * @return the relaxed term or {@code null} if it is always true
     */
    private static String relax(String filter, int[] position) {
        int start = position[0];
        position[0]++;
        char operator = filter.charAt(position[0]);
        String result;
        if (operator == '&' || operator == '|' || operator == '!') {
            position[0]++;
            StringBuilder terms = new StringBuilder();
            boolean changed = false;
            boolean always = false;
            int count = 0;
            while (filter.charAt(position[0]) == '(') {
                int termStart = position[0];
                String term = relax(filter, position);
                if (term == null) {
                    changed = true;
                    always |= operator == '|';
                } else {
                    changed |= !term.equals(filter.substring(termStart, position[0]));
                    terms.append(term);
                    count++;
                }
            }
            if (operator == '!') {
                result = changed ? null : "(!" + terms + ")";
            } else if (always || count == 0) {
                result = null;
            } else {
                result = "(" + operator + terms + ")";
            }
        } else {
            int end = position[0];
            while (filter.charAt(end) != ')') {
                end += filter.charAt(end) == '\\' ? 2 : 1;
            }
            String term = filter.substring(start, end + 1);
            String attribute = filter.substring(start + 1, end).split("[=<>~]", 2)[0].trim();
            result = SYMBOLIC_NAME.equalsIgnoreCase(attribute) || VERSION.equalsIgnoreCase(attribute) ? null : term;
            position[0] = end;
        }
        // the closing parenthesis
        position[0]++;
        return result;
    }

    static boolean matches(String filter, Map<String, Object> attributes) {
        try {
            return FrameworkUtil.createFilter(filter).matches(new Hashtable<>(attributes));
        } catch (InvalidSyntaxException e) {
            return true;
        }
    }

    /**
     * The attributes of a capability of a fragment, as provided by the host.
     */
    static Map<String, Object> hostedAttributes(Map<String, Object> declared, String symbolicName,
            Object version) {
        if (!declared.containsKey(SYMBOLIC_NAME) && !declared.containsKey(VERSION)) {
            return declared;
        }
        Map<String, Object> hosted = new HashMap<>(declared);
        if (declared.containsKey(SYMBOLIC_NAME)) {
            hosted.put(SYMBOLIC_NAME, symbolicName);
        }
        if (declared.containsKey(VERSION)) {
            hosted.put(VERSION, version);
        }
        return Collections.unmodifiableMap(hosted);
    }
}
