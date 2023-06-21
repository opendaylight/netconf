/*
 * Copyright (c) 2023 Orange and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;

/**
 * OpenDaylight extension parameter. When used as {@code odl-child-nodes-only=true}, it will instruct the listener
 * streams to only emit child nodes.
 */
public final class ChildNodesOnlyParam implements RestconfQueryParam<ChildNodesOnlyParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final String uriName = "odl-child-nodes-only";

    private static final @NonNull URI CAPABILITY =
        URI.create("urn:opendaylight:params:restconf:capability:child-nodes-only:1.0");
    private static final @NonNull ChildNodesOnlyParam FALSE = new ChildNodesOnlyParam(false);
    private static final @NonNull ChildNodesOnlyParam TRUE = new ChildNodesOnlyParam(true);

    private final boolean value;

    private ChildNodesOnlyParam(final boolean value) {
        this.value = value;
    }

    public static @NonNull ChildNodesOnlyParam of(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static @NonNull ChildNodesOnlyParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "false" -> FALSE;
            case "true" -> TRUE;
            default -> throw new IllegalArgumentException("Value can be 'false' or 'true', not '" + uriValue + "'");
        };
    }

    @Override
    public Class<ChildNodesOnlyParam> javaClass() {
        return ChildNodesOnlyParam.class;
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

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
