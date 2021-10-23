/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * This class represents a {@code depth} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.3">RFC8040 section 4.8.2</a>.
 */
public final class DepthParameter implements Immutable {
    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:depth:1.0");

    private final int value;

    private DepthParameter(final int value) {
        this.value = value;
        checkArgument(value >= 1 && value <= 65535);
    }

    public static DepthParameter of(final int value) {
        return new DepthParameter(value);
    }

    public static @Nullable DepthParameter forUriValue(final String uriValue) {
        return uriValue.equals("unbounded") ? null : of(Integer.parseUnsignedInt(uriValue, 10));
    }

    public int value() {
        return value;
    }

    public static @NonNull String uriName() {
        return "depth";
    }

    public @NonNull String uriValue() {
        return String.valueOf(value);
    }

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
