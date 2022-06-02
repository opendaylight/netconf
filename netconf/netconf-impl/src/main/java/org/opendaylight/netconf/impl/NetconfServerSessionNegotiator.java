/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalAddress;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSessionNegotiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfServerSessionNegotiator
        extends AbstractNetconfSessionNegotiator<NetconfServerSession, NetconfServerSessionListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSessionNegotiator.class);
    private static final String UNKNOWN = "unknown";

    private final long sessionId;

    protected NetconfServerSessionNegotiator(final NetconfServerSessionPreferences sessionPreferences,
            final Promise<NetconfServerSession> promise, final Channel channel, final Timer timer,
            final NetconfServerSessionListener sessionListener, final long connectionTimeoutMillis) {
        super(sessionPreferences.getHelloMessage(), promise, channel, timer, sessionListener, connectionTimeoutMillis);
        sessionId = sessionPreferences.getSessionId();
    }

    @Override
    protected void handleMessage(final NetconfHelloMessage netconfMessage) throws NetconfDocumentedException {
        NetconfServerSession session = getSessionForHelloMessage(netconfMessage);
        replaceHelloMessageInboundHandler(session);
        // Negotiation successful after all non hello messages were processed
        negotiationSuccessful(session);
    }

    @Override
    protected NetconfServerSession getSession(final NetconfServerSessionListener sessionListener, final Channel channel,
            final NetconfHelloMessage message) {
        final var additionalHeader = message.getAdditionalHeader();
        final var parsedHeader = additionalHeader.orElseGet(() -> {
            final var hostName = getHostName(channel.localAddress());
            return new NetconfHelloMessageAdditionalHeader(UNKNOWN, hostName.getValue(), hostName.getKey(), "tcp",
                "client");
        });

        LOG.debug("Additional header from hello parsed as {} from {}", parsedHeader, additionalHeader);
        return new NetconfServerSession(sessionListener, channel, sessionId, parsedHeader);
    }

    /**
     * Get a name of the host.
     *
     * @param socketAddress type of socket address LocalAddress, or
     *                      InetSocketAddress, for others returns unknown
     * @return Two values - port and host of socket address
     */
    protected static Map.Entry<String, String> getHostName(final SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            final var inetSocketAddress = (InetSocketAddress) socketAddress;
            return new SimpleImmutableEntry<>(Integer.toString(inetSocketAddress.getPort()),
                    inetSocketAddress.getHostString());
        } else if (socketAddress instanceof LocalAddress) {
            return new SimpleImmutableEntry<>(UNKNOWN, ((LocalAddress) socketAddress).id());
        } else {
            return new SimpleImmutableEntry<>(UNKNOWN, UNKNOWN);
        }
    }
}
