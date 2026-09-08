/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Limits on inbound HTTP requests, each in the range {@code 1..Integer.MAX_VALUE}.
 *
 * @param maxInitialLineLength maximum HTTP/1.1 request line length
 * @param maxHeaderSize maximum HTTP header section size
 * @param maxRequestChunkSize maximum HTTP/1.1 decoder chunk size
 * @param maxRequestBodySize maximum aggregated request body size
 */
@NonNullByDefault
public record HttpRequestLimits(Uint32 maxInitialLineLength, Uint32 maxHeaderSize, Uint32 maxRequestChunkSize,
        Uint32 maxRequestBodySize) {
    public HttpRequestLimits {
        requireIntRange("HTTP/1.1 request line length", maxInitialLineLength);
        requireIntRange("HTTP header size", maxHeaderSize);
        requireIntRange("HTTP/1.1 request decoder chunk size", maxRequestChunkSize);
        requireIntRange("HTTP request body size", maxRequestBodySize);
    }

    private static void requireIntRange(final String what, final Uint32 value) {
        if (requireNonNull(value).longValue() < 1) {
            throw new IllegalArgumentException(what + " must be at least one byte");
        }
        if (value.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + " must not exceed " + Integer.MAX_VALUE);
        }
    }
}
