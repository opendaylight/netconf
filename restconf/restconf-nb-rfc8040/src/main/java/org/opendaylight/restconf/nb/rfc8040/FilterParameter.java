/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * This class represents a {@code filter} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.4">RFC8040 section 4.8.4</a>.
 */

public final class FilterParameter implements Immutable {
    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:filter:1.0");

    // FIXME: can we have a parsed, but not bound version of an XPath, please?
    private final @NonNull String value;

    private FilterParameter(final String value) {
        this.value = requireNonNull(value);
    }

    public static @NonNull FilterParameter forUriValue(final String uriValue) {
        return new FilterParameter(uriValue);
    }

    public static @NonNull String uriName() {
        return "filter";
    }

    public @NonNull String uriValue() {
        return value;
    }

    public static @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
