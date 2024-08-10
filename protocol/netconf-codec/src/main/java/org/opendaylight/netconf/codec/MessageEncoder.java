/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An encoder of {@link NetconfMessage} to a series of bytes.
 */
public final class MessageEncoder extends MessageToByteEncoder<NetconfMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageEncoder.class);

    private @NonNull FramingSupport framing = FramingSupport.eom();
    private @NonNull MessageWriter writer;

    public MessageEncoder(final MessageWriter writer) {
        super(NetconfMessage.class);
        this.writer = requireNonNull(writer);
    }

    public void setFraming(final FramingSupport newFraming) {
        framing = requireNonNull(newFraming);
    }

    public void setWriter(final MessageWriter newWriter) {
        writer = requireNonNull(newWriter);
    }

    @VisibleForTesting
    public @NonNull FramingSupport framing() {
        return framing;
    }

    @VisibleForTesting
    public @NonNull MessageWriter writer() {
        return writer;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws Exception {
        LOG.trace("Sent to encode : {}", msg);
        framing.writeMessage(ctx.alloc(), msg, writer, out);
    }
}
