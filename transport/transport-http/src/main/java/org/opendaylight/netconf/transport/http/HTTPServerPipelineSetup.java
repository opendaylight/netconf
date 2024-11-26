/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A particular HTTP transport pipeline setup. It implies the object model and request semantics a
 * {@link HTTPTransportChannel} operates on. This enumeration is reused as a user event on channels attached to a
 * {@link HTTPServer}.
 */
@NonNullByDefault
public enum HTTPServerPipelineSetup {
    /**
     * HTTP/1.1 <a href="https://www.rfc-editor.org/rfc/rfc9112#section-9.3.2">pipeline semantics</a> working with
     * {@link FullHttpRequest} and {@link FullHttpResponse}.
     */
    HTTP_11("HTTP/1.1"),
    /**
     * HTTP/2+ <a href="https://www.rfc-editor.org/rfc/rfc9113#section-5.1.2">concurrent semantics</a> working with
     * {@link FullHttpRequest} and {@link FullHttpResponse}.
     */
    HTTP_2("HTTP/2");

    private final String semantics;

    HTTPServerPipelineSetup(final String semantics) {
        this.semantics = requireNonNull(semantics);
    }

    @Override
    public String toString() {
        return semantics;
    }
}
