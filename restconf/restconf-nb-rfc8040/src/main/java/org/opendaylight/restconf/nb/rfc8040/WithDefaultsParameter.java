/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Enumeration of possible {@code with-defaults} parameter values as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.9">RFC8040, section 4.8.9</a>.
 */
public enum WithDefaultsParameter {
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

    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:with-defaults:1.0");

    private final @NonNull String uriValue;

    WithDefaultsParameter(final String uriValue) {
        this.uriValue = requireNonNull(uriValue);
    }

    public static @NonNull String uriName() {
        return "with-defaults";
    }

    public @NonNull String uriValue() {
        return uriValue;
    }

    public static @Nullable WithDefaultsParameter forUriValue(final String uriValue) {
        switch (uriValue) {
            case "explicit":
                return EXPLICIT;
            case "report-all":
                return REPORT_ALL;
            case "report-all-tagged":
                return REPORT_ALL_TAGGED;
            case "trim":
                return TRIM;
            default:
                return null;
        }
    }

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
