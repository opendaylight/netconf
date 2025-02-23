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
 * OpenDaylight extension parameter. When used as {@code odl-skip-notification-data=true}, it will instruct the listener
 * streams to prune data from notifications.
 */
public final class SkipNotificationDataParam implements RestconfQueryParam<SkipNotificationDataParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final String uriName = "odl-skip-notification-data";

    private static final @NonNull SkipNotificationDataParam FALSE = new SkipNotificationDataParam(false);
    private static final @NonNull SkipNotificationDataParam TRUE = new SkipNotificationDataParam(true);

    private final boolean value;

    private SkipNotificationDataParam(final boolean value) {
        this.value = value;
    }

    public static @NonNull SkipNotificationDataParam of(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static @NonNull SkipNotificationDataParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "false" -> FALSE;
            case "true" -> TRUE;
            default -> throw new IllegalArgumentException("Value can be 'false' or 'true', not '" + uriValue + "'");
        };
    }

    @Override
    public Class<SkipNotificationDataParam> javaClass() {
        return SkipNotificationDataParam.class;
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
