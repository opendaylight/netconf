/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalAddress;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import org.checkerframework.checker.index.qual.NonNegative;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSessionNegotiator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfServerSessionNegotiator
        extends AbstractNetconfSessionNegotiator<NetconfServerSession, NetconfServerSessionListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSessionNegotiator.class);
    private static final String UNKNOWN = "unknown";

    private final @NonNull SessionIdType sessionId;

    NetconfServerSessionNegotiator(final HelloMessage hello, final SessionIdType sessionId,
            final Promise<NetconfServerSession> promise, final Channel channel, final Timer timer,
            final NetconfServerSessionListener sessionListener, final long connectionTimeoutMillis,
            final @NonNegative int maximumIncomingChunkSize) {
        super(hello, promise, channel, timer, sessionListener, connectionTimeoutMillis,
            maximumIncomingChunkSize);
        this.sessionId = requireNonNull(sessionId);
    }

    @Override
    protected void handleMessage(final HelloMessage netconfMessage) throws NetconfDocumentedException {
        NetconfServerSession session = getSessionForHelloMessage(netconfMessage);
        replaceHelloMessageInboundHandler(session);
        // Negotiation successful after all non hello messages were processed
        negotiationSuccessful(session);
    }

    @Override
    protected NetconfServerSession getSession(final NetconfServerSessionListener sessionListener, final Channel channel,
            final HelloMessage message) {
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
        if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
            return new SimpleImmutableEntry<>(Integer.toString(inetSocketAddress.getPort()),
                    inetSocketAddress.getHostString());
        } else if (socketAddress instanceof LocalAddress localAddress) {
            return Map.entry(UNKNOWN, localAddress.id());
        } else {
            return Map.entry(UNKNOWN, UNKNOWN);
        }
    }
}
