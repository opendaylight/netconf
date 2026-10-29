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
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

/**
 * Reference-counted request headers paired with an incrementally received body.
 *
 * <p>Releasing the last reference closes an unsubmitted body, including when authentication rejects the request or
 * a session discards its queue. Once submitted, the executor owns the body independently of these headers.
 */
final class StreamingHttpRequest extends DefaultHttpRequest implements ReferenceCounted {
    private final RequestBodyInputStream body = new RequestBodyInputStream();

    private boolean transferred;
    private final AbstractReferenceCounted references = new AbstractReferenceCounted() {
        @Override
        protected void deallocate() {
            if (!transferred) {
                body.close();
            }
        }

        @Override
        public ReferenceCounted touch(final Object hint) {
            return this;
        }
    };

    StreamingHttpRequest(final HttpRequest request) {
        super(request.protocolVersion(), request.method(), request.uri(), request.headers());
        // Headers are shared; the dispatcher has already handled Expect before wrapping.
        setDecoderResult(request.decoderResult());
    }

    RequestBodyInputStream body() {
        return body;
    }

    RequestBodyInputStream takeBody() {
        transferred = true;
        return body;
    }

    @Override
    public int refCnt() {
        return references.refCnt();
    }

    @Override
    public StreamingHttpRequest retain() {
        references.retain();
        return this;
    }

    @Override
    public StreamingHttpRequest retain(final int increment) {
        references.retain(increment);
        return this;
    }

    @Override
    public StreamingHttpRequest touch() {
        return this;
    }

    @Override
    public StreamingHttpRequest touch(final Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return references.release();
    }

    @Override
    public boolean release(final int decrement) {
        return references.release(decrement);
    }
}
