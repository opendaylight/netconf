/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.with.defaults.rev110601.WithDefaultsMode;

/**
 * Enumeration of possible {@code with-defaults} parameter values as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.9">RFC8040, section 4.8.9</a>. This is an equivalent
 * of with-defaults retrieval mode as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc6243#section-3">RFC6243 section 3</a> and expressed as the
 * {@code typedef with-defaults-mode}} in the corresponding YANG model.
 */
public enum WithDefaultsParam implements RestconfQueryParam<WithDefaultsParam> {
    /**
     * Data nodes set to the YANG default by the client are reported.
     */
    EXPLICIT(WithDefaultsMode.Explicit),
    /**
     * All data nodes are reported.
     */
    REPORT_ALL(WithDefaultsMode.ReportAll),
    /**
     * All data nodes are reported, and defaults are tagged.
     */
    REPORT_ALL_TAGGED(WithDefaultsMode.ReportAllTagged),
    /**
     * Data nodes set to the YANG default are not reported.
     */
    TRIM(WithDefaultsMode.Trim);

    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "with-defaults";

    private final @NonNull WithDefaultsMode mode;

    WithDefaultsParam(final WithDefaultsMode mode) {
        this.mode = requireNonNull(mode);
    }

    public static @NonNull WithDefaultsParam of(final WithDefaultsMode mode) {
        return switch (mode) {
            case Explicit -> EXPLICIT;
            case ReportAll -> REPORT_ALL;
            case ReportAllTagged -> REPORT_ALL_TAGGED;
            case Trim -> TRIM;
        };
    }

    public static @NonNull WithDefaultsParam forUriValue(final String uriValue) {
        try {
            return of(WithDefaultsMode.ofName(uriValue));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + uriName + " value: " + e.getMessage(), e);
        }
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
        return mode.getName();
    }

    public @NonNull WithDefaultsMode mode() {
        return mode;
    }
}
