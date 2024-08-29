/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.codec.EOMFrameDecoder;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.HelloMessageWriter;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TransportChannelListener} which establishes a {@link NetconfChannel} on top of any established
 * {@link TransportChannel}.
 */
@NonNullByDefault
public abstract class NetconfChannelListener implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfChannelListener.class);

    public static final String NETCONF_SESSION_NEGOTIATOR = "negotiator";

    @Override
    public final void onTransportChannelEstablished(final TransportChannel channel) {
        LOG.debug("Transport channel {} established", channel);

        final var messageEncoder = new MessageEncoder(HelloMessageWriter.pretty());
        channel.channel().pipeline()
            .addLast(FrameDecoder.HANDLER_NAME, new EOMFrameDecoder())
            // Special decoding handler for hello message to parse additional header if available, it is thrown away
            // after successful negotiation
            .addLast(MessageDecoder.HANDLER_NAME, new HelloXMLMessageDecoder())
            // Special encoding handler for hello message to include additional header if available, it is thrown away
            // after successful negotiation
            .addLast("netconfMessageEncoder", messageEncoder);

        onNetconfChannelEstablished(new NetconfChannel(channel, messageEncoder));
    }

    protected abstract void onNetconfChannelEstablished(NetconfChannel channel);
}
