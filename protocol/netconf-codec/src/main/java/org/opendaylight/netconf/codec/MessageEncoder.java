/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An encoder of {@link NetconfMessage} to a series of bytes.
 */
public abstract class MessageEncoder extends MessageToByteEncoder<NetconfMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageEncoder.class);

    /**
     * The name of the handler providing message encoding.
     */
    public static final @NonNull String HANDLER_NAME = "netconfMessageEncoder";

    protected MessageEncoder() {
        super(NetconfMessage.class);
    }

    @Override
    protected final void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws Exception {
        LOG.trace("Sent to encode : {}", msg);
        try (var os = new ByteBufOutputStream(out)) {
            encodeTo(msg, os);
        }
    }

    protected abstract void encodeTo(NetconfMessage msg, OutputStream out) throws Exception;
}
