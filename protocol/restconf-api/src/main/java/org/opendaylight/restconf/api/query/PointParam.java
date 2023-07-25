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

/**
 * This class represents a {@code point} parameter as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.6">RFC8040 section 4.8.6</a>.
 */
public final class PointParam implements RestconfQueryParam<PointParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "point";

    // FIXME: This should be ApiPath
    private final @NonNull String value;

    private PointParam(final String value) {
        this.value = requireNonNull(value);
    }

    @Override
    public Class<PointParam> javaClass() {
        return PointParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return value;
    }

    public static @NonNull PointParam forUriValue(final String uriValue) {
        return new PointParam(uriValue);
    }

    public @NonNull String value() {
        return value;
    }
}
