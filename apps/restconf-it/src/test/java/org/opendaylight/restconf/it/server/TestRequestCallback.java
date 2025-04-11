/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;

public final class TestRequestCallback implements FutureCallback<FullHttpResponse> {
    private volatile boolean completed;
    private volatile FullHttpResponse response;

    @Override
    public void onSuccess(final FullHttpResponse result) {
        // detach response object from channel, so message content is not lost after client is disconnected
        final var content = Unpooled.wrappedBuffer(ByteBufUtil.getBytes(result.content()));
        final var copy = new DefaultFullHttpResponse(result.protocolVersion(), result.status(), content);
        copy.headers().set(result.headers());
        response = copy;
        completed = true;
    }

    @Override
    public void onFailure(final Throwable throwable) {
        completed = true;
        throw new IllegalStateException("Failed request ", throwable);
    }

    public FullHttpResponse response() {
        return response;
    }

    public boolean completed() {
        return completed;
    }
}