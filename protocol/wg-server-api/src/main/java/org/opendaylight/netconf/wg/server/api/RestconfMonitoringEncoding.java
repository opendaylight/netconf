/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.wg.server.api;

import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An opinionated view on what values we can produce for {@code leaf encoding} in the context of
 * {@code ietf-restconf-monitoring.yang}. The name can only be composed of one or more characters matching
 * {@code [a-zA-Z]}.
 *
 * @param value Encoding name, as visible via the stream's {@code access} list's {@code encoding} leaf
 */
@NonNullByDefault
public record RestconfMonitoringEncoding(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-zA-Z]+");

    /**
     * Well-known JSON encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang} as {@code json}.
     */
    public static final RestconfMonitoringEncoding JSON = new RestconfMonitoringEncoding("json");
    /**
     * Well-known XML encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang} as {@code xml}.
     */
    public static final RestconfMonitoringEncoding XML = new RestconfMonitoringEncoding("xml");

    /**
     * Default constructor.
     *
     * @param value the encoding name
     */
    public RestconfMonitoringEncoding {
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("name must match " + PATTERN);
        }
    }

    /**
     * Factory method, taking into consideration well-known values.
     *
     * @param value the encoding name
     * @return A {@link RestconfMonitoringEncoding}
     * @throws IllegalArgumentException if the {@code name} is not a valid encoding name
     */
    public static RestconfMonitoringEncoding of(final String value) {
        return switch (value) {
            case "json" -> JSON;
            case "xml" -> XML;
            default -> new RestconfMonitoringEncoding(value);
        };
    }
}