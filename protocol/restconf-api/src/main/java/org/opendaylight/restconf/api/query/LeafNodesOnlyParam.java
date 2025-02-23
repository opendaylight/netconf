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
 * OpenDaylight extension parameter. When used as {@code odl-leaf-nodes-only=true}, it will instruct the listener
 * streams to only emit leaf nodes.
 */
public final class LeafNodesOnlyParam implements RestconfQueryParam<LeafNodesOnlyParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final String uriName = "odl-leaf-nodes-only";

    private static final @NonNull LeafNodesOnlyParam FALSE = new LeafNodesOnlyParam(false);
    private static final @NonNull LeafNodesOnlyParam TRUE = new LeafNodesOnlyParam(true);

    private final boolean value;

    private LeafNodesOnlyParam(final boolean value) {
        this.value = value;
    }

    public static @NonNull LeafNodesOnlyParam of(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static @NonNull LeafNodesOnlyParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "false" -> FALSE;
            case "true" -> TRUE;
            default -> throw new IllegalArgumentException("Value can be 'false' or 'true', not '" + uriValue + "'");
        };
    }

    @Override
    public Class<LeafNodesOnlyParam> javaClass() {
        return LeafNodesOnlyParam.class;
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
