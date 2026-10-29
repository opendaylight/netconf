/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Request headers paired with an incrementally received body.
 */
final class StreamingHttpRequest extends DefaultHttpRequest {
    private final RequestBodyInputStream body = new RequestBodyInputStream();

    StreamingHttpRequest(final HttpRequest request) {
        super(request.protocolVersion(), request.method(), request.uri(), request.headers());
        setDecoderResult(request.decoderResult());
    }

    RequestBodyInputStream body() {
        return body;
    }
}
