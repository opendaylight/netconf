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
import com.google.common.base.VerifyException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An encoder of {@link NetconfMessage} to a series of bytes.
 */
// TODO: MessageToByteEncoder is forcing us to encode the entire NetconfMessage into a single buffer before it can be
//       passed downstream. We should switch to a plain ChannelOutboundHandlerAdapter and send multiple ByteBufs down
//       the pipeline, completing the Promise with the result of Future returned by the last write(ByteBuf) -- which
//       we expect underlying Channel to handle as required by https://www.rfc-editor.org/rfc/rfc6241#section-2.1
public final class MessageEncoder extends MessageToByteEncoder<NetconfMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageEncoder.class);

    private @NonNull FramingSupport framing = FramingSupport.eom();
    private @NonNull MessageWriter writer;

    private @Nullable MessageWriter nextWriter;

    public MessageEncoder(final MessageWriter writer) {
        super(NetconfMessage.class);
        this.writer = requireNonNull(writer);
    }

    public void setFraming(final FramingSupport newFraming) {
        framing = requireNonNull(newFraming);
        LOG.debug("Switched framing to {}", framing);
    }

    public void setWriter(final MessageWriter newWriter) {
        doSetWriter(requireNonNull(newWriter));
    }

    private void doSetWriter(final @NonNull MessageWriter newWriter) {
        writer = newWriter;
        LOG.debug("Switched writer to {}", writer);
    }

    public void setNextWriter(final MessageWriter newNextWriter) {
        final var local = nextWriter;
        if (local != null) {
            throw new VerifyException("Next writer already set to " + local);
        }
        nextWriter = requireNonNull(newNextWriter);
        LOG.debug("Switching to {} after next message", nextWriter);
    }

    @VisibleForTesting
    public @NonNull FramingSupport framing() {
        return framing;
    }

    @VisibleForTesting
    public @NonNull MessageWriter writer() {
        return writer;
    }

    @VisibleForTesting
    public @Nullable MessageWriter nextWriter() {
        return nextWriter;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws Exception {
        LOG.trace("Sent to encode : {}", msg);
        framing.writeMessage(ctx.alloc(), msg, writer, out);

        // TODO: This is nicer than it used to be, but it is still not pretty.
        //       Next writer should be carried with the message being written, i.e. have a 'MessageWriterNetconfMessage'
        //       class, which has a MessageWriter. When we encounter it, just pick the writer from there -- making state
        //       bound to the event causing them.
        //       Unfortunately this is called from DefaultStartExi, which does not send the message directly, hence
        //       addressing this improvement requires a netconf-server RPC execution refactor.

        final var local = nextWriter;
        if (local != null) {
            nextWriter = null;
            doSetWriter(local);
        }
    }
}
