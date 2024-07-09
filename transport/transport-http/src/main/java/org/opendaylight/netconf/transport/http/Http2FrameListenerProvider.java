/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2FrameListener;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Inbound HTTP/2 frame processor extension interface. Used by {@link Http2ToHttpAdapter} handler to alternate
 * default behavior (HTTP/1 message aggregation) on per stream-id basis.
 */
public interface Http2FrameListenerProvider extends ChannelHandler {

    /**
     * Returns custom {@link Http2FrameListener} implementation for a specific stream to be used to handle
     * inbound HTTP/2 headers and data frames.
     *
     * @param streamId stream-id
     * @return the stream associated frame listener if found, null otherwise
     */
    @Nullable Http2FrameListener getListenerFor(int streamId);
}
