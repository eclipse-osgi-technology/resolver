/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The capabilities of one namespace, additionally grouped by the value of the attribute that is named like the
 * namespace (the package name, the bundle symbolic name, ...).
 * A capability whose primary attribute is a collection or an array is found under each of its elements.
 * Capabilities are kept and compared by identity only, their equals methods are comparatively expensive.
 * Capabilities with a primary value that is not a string can not be selected by its text, so they are
 * always candidates.
 * A requirement is looked up by the value its filter demands of the primary attribute, the filter and the
 * mandatory directive then decide.
 */
public final class CapabilityIndex {

    private static final Map<String, Pattern> MANDATORY_PATTERNS = new ConcurrentHashMap<>();

    private final String namespace;
    private final boolean matchMandatory;
    private final List<Capability> all = new ArrayList<>();
    private final Map<String, List<Capability>> byValue = new HashMap<>();
    private final List<Capability> loose = new ArrayList<>();
    private final Map<Capability, Integer> position = new IdentityHashMap<>();
    private final Map<String, Filter> filters = new ConcurrentHashMap<>();

    /**
     * Indexes the capabilities of one namespace.
     *
     * @param namespace the namespace of the capabilities to index
     * @param resources the resources to take the capabilities from, their order is the order of the results
     */
    public CapabilityIndex(String namespace, Collection<Resource> resources) {
        this.namespace = namespace;
        this.matchMandatory = namespace.startsWith("osgi.wiring.");
        for (Resource resource : resources) {
            for (Capability capability : resource.getCapabilities(namespace)) {
                position.put(capability, all.size());
                all.add(capability);
                index(capability, capability.getAttributes().get(namespace));
            }
        }
    }

    private void index(Capability capability, Object primary) {
        Collection<?> values = null;
        if (primary instanceof Collection) {
            values = (Collection<?>) primary;
        } else if (primary instanceof Object[]) {
            values = Arrays.asList((Object[]) primary);
        }
        if (values == null) {
            add(capability, primary);
            return;
        }
        boolean isLoose = values.isEmpty();
        List<Object> seen = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof String) {
                if (!seen.contains(value)) {
                    seen.add(value);
                    add(capability, value);
                }
            } else {
                isLoose = true;
            }
        }
        if (isLoose) {
            loose.add(capability);
        }
    }

    private void add(Capability capability, Object value) {
        if (value instanceof String) {
            byValue.computeIfAbsent((String) value, k -> new ArrayList<>()).add(capability);
        } else {
            loose.add(capability);
        }
    }

    /**
     * Returns every capability of the namespace.
     *
     * @return all capabilities in the order of the resources, not to be modified
     */
    public List<Capability> all() {
        return all;
    }

    /**
     * Returns every capability that can match a requirement for the given value of the primary attribute.
     *
     * @param primary the value of the attribute that is named like the namespace
     * @return the capabilities having that value and those without a string value, in the order of the
     *         resources, not to be modified
     */
    public List<Capability> candidates(String primary) {
        List<Capability> exact = byValue.getOrDefault(primary, Collections.<Capability> emptyList());
        if (loose.isEmpty()) {
            return exact;
        }
        List<Capability> merged = new ArrayList<>(exact);
        merged.addAll(loose);
        merged.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
        return merged;
    }

    /**
     * The capabilities that satisfy the filter of the requirement and, for the osgi.wiring.* namespaces, name all
     * attributes of their mandatory directive in it, in the order of the resources.
     *
     * @param requirement the requirement to find the capabilities for, of the namespace of this index
     * @return a new list of the matching capabilities, the caller may modify it
     * @throws IllegalArgumentException if the filter is invalid
     */
    public List<Capability> findProviders(Requirement requirement) {
        String filterString = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        Filter filter = filter(filterString);
        String primary = primaryValue(filterString, namespace);
        List<Capability> result = new ArrayList<>();
        for (Capability capability : primary == null ? all : candidates(primary)) {
            if ((filter == null || filter.matches(capability.getAttributes()))
                    && (!matchMandatory || mandatoryAttributesAreRequested(capability, filterString))) {
                result.add(capability);
            }
        }
        return result;
    }

    /**
     * Returns the namespace this index is built for.
     *
     * @return the namespace
     */
    public String namespace() {
        return namespace;
    }

    private Filter filter(String filterString) {
        if (filterString == null) {
            return null;
        }
        Filter filter = filters.get(filterString);
        if (filter == null) {
            try {
                filter = FrameworkUtil.createFilter(filterString);
            } catch (InvalidSyntaxException e) {
                throw new IllegalArgumentException("Invalid filter " + filterString, e);
            }
            filters.put(filterString, filter);
        }
        return filter;
    }

    /**
     * The value the filter demands of the primary attribute of the namespace if the filter is that comparison or
     * a conjunction containing it, only used to preselect, the filter itself still decides.
     */
    static String primaryValue(String filter, String namespace) {
        if (filter == null) {
            return null;
        }
        String prefix = "(" + namespace + "=";
        String text = filter.trim();
        if (text.startsWith("(&")) {
            int depth = 0;
            int start = 2;
            for (int i = 2; i < text.length() - 1; i++) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '(') {
                    if (depth++ == 0) {
                        start = i;
                    }
                } else if (c == ')' && --depth == 0) {
                    String value = simple(text.substring(start, i + 1), prefix);
                    if (value != null) {
                        return value;
                    }
                }
            }
            return null;
        }
        return simple(text, prefix);
    }

    private static String simple(String term, String prefix) {
        if (!term.startsWith(prefix) || !term.endsWith(")")) {
            return null;
        }
        String value = term.substring(prefix.length(), term.length() - 1);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '\\' || c == '(' || c == ')') {
                return null;
            }
        }
        return value;
    }

    /** All attributes the mandatory directive names have to be mentioned in the requirement filter. */
    private static boolean mandatoryAttributesAreRequested(Capability capability, String filter) {
        String mandatory = capability.getDirectives().get("mandatory");
        if (mandatory == null) {
            return true;
        }
        for (String attribute : mandatory.split("\\s*,\\s*")) {
            if (filter == null || !MANDATORY_PATTERNS
                    .computeIfAbsent(attribute, a -> Pattern.compile("\\(" + Pattern.quote(a) + "\\s*[=<>~]"))
                    .matcher(filter).find()) {
                return false;
            }
        }
        return true;
    }
}
