/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import org.eclipse.jdt.annotation.NonNull;

/**
 * OpenDaylight extension parameter. When used as {@code odl-pretty-print=true}, it will instruct outbound XML/JSON
 * formatters to make the output easier for humans to understand.
 */
public final class PrettyPrintParam implements RestconfQueryParam<PrettyPrintParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final String uriName = "odl-pretty-print";

    public static final @NonNull PrettyPrintParam FALSE = new PrettyPrintParam(false);
    public static final @NonNull PrettyPrintParam TRUE = new PrettyPrintParam(true);

    private final boolean value;

    private PrettyPrintParam(final boolean value) {
        this.value = value;
    }

    public static @NonNull PrettyPrintParam of(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static @NonNull PrettyPrintParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "false" -> FALSE;
            case "true" -> TRUE;
            default -> throw new IllegalArgumentException(
                "Invalid " + uriName + " value: Value can be 'false' or 'true', not '" + uriValue + "'");
        };
    }

    @Override
    public Class<PrettyPrintParam> javaClass() {
        return PrettyPrintParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    @Override
    public String paramValue() {
        return String.valueOf(value);
    }

    public boolean value() {
        return value;
    }
}
