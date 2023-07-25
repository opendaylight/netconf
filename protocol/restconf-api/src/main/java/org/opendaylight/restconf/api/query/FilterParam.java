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
 * This class represents a {@code filter} parameter as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.4">RFC8040 section 4.8.4</a>.
 */

public final class FilterParam implements RestconfQueryParam<FilterParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "filter";

    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:filter:1.0");

    // FIXME: can we have a parsed, but not bound version of an XPath, please?
    private final @NonNull String value;

    private FilterParam(final String value) {
        this.value = requireNonNull(value);
    }

    @Override
    public Class<FilterParam> javaClass() {
        return FilterParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return value;
    }

    public static @NonNull FilterParam forUriValue(final String uriValue) {
        return new FilterParam(uriValue);
    }

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
