/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * This class represents a {@code depth} parameter as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.2">RFC8040 section 4.8.2</a>.
 */
public final class DepthParam implements RestconfQueryParam<DepthParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "depth";

    private static final @NonNull DepthParam MIN = new DepthParam(1);
    private static final @NonNull DepthParam MAX = new DepthParam(65535);

    private final int value;

    private DepthParam(final int value) {
        this.value = value;
    }

    public static @NonNull DepthParam of(final int value) {
        return switch (value) {
            case 1 -> min();
            case 65535 -> max();
            default -> {
                if (value < 1 || value > 65535) {
                    throw new IllegalArgumentException(value + " is not between 1 and 65535");
                }
                yield new DepthParam(value);
            }
        };
    }

    @Override
    public Class<DepthParam> javaClass() {
        return DepthParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return String.valueOf(value);
    }

    public static @NonNull DepthParam min() {
        return MIN;
    }

    public static @NonNull DepthParam max() {
        return MAX;
    }

    public static @Nullable DepthParam forUriValue(final String uriValue) {
        if ("unbounded".equals(uriValue)) {
            return null;
        }

        final int value;
        try {
            value = Integer.parseUnsignedInt(uriValue, 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "The depth parameter must be \"unbounded\" or an integer between 1 and 65535. \""
                    + uriValue + "\" is not a valid integer", e);
        }
        try {
            return of(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "The depth parameter must be \"unbounded\" or an integer between 1 and 65535. "
                    + e.getMessage(), e);
        }
    }

    public int value() {
        return value;
    }
}
