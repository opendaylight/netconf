/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.handler.codec.ByteToMessageDecoder;
import org.eclipse.jdt.annotation.NonNull;

/**
 * An channel handler assembling inbound messages from specified framing.
 */
// TODO: Reconsider use of ByteToMessageDecoder here. While the Cumulator use is nice, I think we can do better if we
//       do our buffer management ourselves:
//       - we already use a CompositeByteBuf in ChunkedFrameDecoder with a buch of state tracking
//       - we have searchIndex in order to optimize resumed searches, so taking ownership of the buffer should not be
//         a big problem
//
//       ChannelInboundHandlerAdapter should be a good first step, allowing us to focus on the implementation rather
//       than the relationship to MessageDecoder.
//
//       Ultimately this should become a base class disconnected from ChannelHandler, instances of which are given out
//       by FramingSupport to MessageEncoder, which then handles interactions between those objects and the Netty
//       channel, becoming also ChannelInboundHandler. Note that when that happens, channel initialization (now in
//       AbstractChannelInitializer) must be updated to place MessageEncoder *before* MessageDecoder.
public abstract sealed class FrameDecoder extends ByteToMessageDecoder permits ChunkedFrameDecoder, EOMFrameDecoder {
    /**
     * The name of the handler providing frame decoding.
     */
    public static final @NonNull String HANDLER_NAME = "frameDecoder";
}
