/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Enumeration of possible {@code with-defaults} parameter values as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.9">RFC8040, section 4.8.9</a>.
 */
public enum WithDefaultsParam implements RestconfQueryParam<WithDefaultsParam> {
    /**
     * Data nodes set to the YANG default by the client are reported.
     */
    EXPLICIT("explicit"),
    /**
     * All data nodes are reported.
     */
    REPORT_ALL("report-all"),
    /**
     * All data nodes are reported, and defaults are tagged.
     */
    REPORT_ALL_TAGGED("report-all-tagged"),
    /**
     * Data nodes set to the YANG default are not reported.
     */
    TRIM("trim");

    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "with-defaults";

    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:with-defaults:1.0");

    private final @NonNull String uriValue;

    WithDefaultsParam(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    @Override
    public Class<WithDefaultsParam> javaClass() {
        return WithDefaultsParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return uriValue;
    }

    public static @NonNull WithDefaultsParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "explicit" -> EXPLICIT;
            case "report-all" -> REPORT_ALL;
            case "report-all-tagged" -> REPORT_ALL_TAGGED;
            case "trim" -> TRIM;
            default -> throw new IllegalArgumentException(
                "Value can be 'explicit', 'report-all', 'report-all-tagged' or 'trim', not '" + uriValue + "'");
        };
    }

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
