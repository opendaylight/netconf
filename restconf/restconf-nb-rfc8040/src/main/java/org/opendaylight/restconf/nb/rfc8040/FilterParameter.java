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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * This class represents a {@code filter} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.4">RFC8040 section 4.8.4</a>.
 */
@NonNullByDefault
public final class FilterParameter implements Immutable {
    private static final URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:filter:1.0");

    private final String value;

    private FilterParameter(final String value) {
        this.value = requireNonNull(value);
    }

    public static @Nullable FilterParameter forUriValue(final String uriValue) {
        // FIXME: can we have a parsed, but not bound version of an XPath, please?
        return new FilterParameter(uriValue);
    }

    public static String uriName() {
        return "filter";
    }

    public String uriValue() {
        return value;
    }

    public static URI capabilityUri() {
        return CAPABILITY;
    }
}
