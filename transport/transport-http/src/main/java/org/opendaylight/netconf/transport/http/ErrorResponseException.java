/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;

// FIXME document purpose
public final class ErrorResponseException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final @NonNull CharSequence contentType;

    private final int statusCode;

    public ErrorResponseException(final int statusCode, final String message,
            final @NonNull CharSequence contentType) {
        super(message);
        this.contentType = requireNonNull(contentType);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public CharSequence contentType() {
        return contentType;
    }
}
