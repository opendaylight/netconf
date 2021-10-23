/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * This class represents a {@code point} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.4">RFC8040 section 4.8.4</a>.
 */
@NonNullByDefault
public final class PointParameter implements Immutable {
    // FIXME: This should be ApiPath
    private final String value;

    private PointParameter(final String value) {
        this.value = requireNonNull(value);
    }

    public static PointParameter forUriValue(final String uriValue) {
        return new PointParameter(uriValue);
    }

    public static String uriName() {
        return "point";
    }

    public String value() {
        return value;
    }
}
