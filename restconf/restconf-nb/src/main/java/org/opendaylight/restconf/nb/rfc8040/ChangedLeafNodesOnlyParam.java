/*
 * Copyright (c) 2022 FRINX s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;

/**
 * OpenDaylight extension parameter. When used as {@code changed-leaf-nodes-only=true}, it will instruct the listener
 * streams to only emit leaf nodes.
 */
public final class ChangedLeafNodesOnlyParam implements RestconfQueryParam<ChangedLeafNodesOnlyParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final String uriName = "changed-leaf-nodes-only";

    private static final @NonNull URI CAPABILITY =
            URI.create("urn:opendaylight:params:restconf:capability:changed-leaf-nodes-only:1.0");
    private static final @NonNull ChangedLeafNodesOnlyParam FALSE = new ChangedLeafNodesOnlyParam(false);
    private static final @NonNull ChangedLeafNodesOnlyParam TRUE = new ChangedLeafNodesOnlyParam(true);

    private final boolean value;

    private ChangedLeafNodesOnlyParam(final boolean value) {
        this.value = value;
    }

    public static @NonNull ChangedLeafNodesOnlyParam of(final boolean value) {
        return value ? TRUE : FALSE;
    }

    public static @NonNull ChangedLeafNodesOnlyParam forUriValue(final String uriValue) {
        return switch (uriValue) {
            case "false" -> FALSE;
            case "true" -> TRUE;
            default -> throw new IllegalArgumentException("Value can be 'false' or 'true', not '" + uriValue + "'");
        };
    }

    @Override
    public Class<@NonNull ChangedLeafNodesOnlyParam> javaClass() {
        return ChangedLeafNodesOnlyParam.class;
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
