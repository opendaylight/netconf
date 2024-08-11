/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.codec.XMLMessageDecoder;
import org.opendaylight.netconf.codec.XMLMessageWriter;
import org.opendaylight.netconf.nettyutil.AbstractNetconfExiSession;
import org.opendaylight.netconf.server.api.monitoring.JavaCommonCounters;
import org.opendaylight.netconf.server.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.server.spi.MutableCommonCounters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.Session1Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: separate out API and implementation, because at it currently stands we are leaking all of
//        ChannelInboundHandlerAdapter, potentially leading to surprises.
public final class NetconfServerSession extends AbstractNetconfExiSession<NetconfServerSession,
        NetconfServerSessionListener> implements NetconfManagementSession {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSession.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_TIME_PATTERN;

    static {
        verify(DateAndTime.PATTERN_CONSTANTS.size() == 1);
        DATE_TIME_PATTERN = Pattern.compile(DateAndTime.PATTERN_CONSTANTS.get(0));
    }

    private final @NonNull MutableCommonCounters commonCounters = MutableCommonCounters.of();
    private final NetconfHelloMessageAdditionalHeader header;
    private final NetconfServerSessionListener sessionListener;

    private ZonedDateTime loginTime;
    private volatile boolean delayedClose;

    public NetconfServerSession(final NetconfServerSessionListener sessionListener, final Channel channel,
                                final SessionIdType sessionId, final NetconfHelloMessageAdditionalHeader header) {
        super(sessionListener, channel, sessionId);
        this.header = header;
        this.sessionListener = requireNonNull(sessionListener);
        LOG.debug("Session {} created", this);
    }

    public @NonNull JavaCommonCounters commonCounters() {
        return commonCounters;
    }

    @Override
    protected void sessionUp() {
        checkState(loginTime == null, "Session is already up");
        loginTime = Instant.now().atZone(ZoneId.systemDefault());
        super.sessionUp();
    }

    /**
     * Close this session after next message is sent.
     * Suitable for close rpc that needs to send ok response before the session is closed.
     */
    public void delayedClose() {
        delayedClose = true;
    }

    @Override
    public ChannelFuture sendMessage(final NetconfMessage netconfMessage) {
        final ChannelFuture channelFuture = super.sendMessage(netconfMessage);
        if (netconfMessage instanceof NotificationMessage notification) {
            commonCounters.incOutNotifications();
            sessionListener.onNotification(this, notification);
        }
        // delayed close was set, close after the message was sent
        if (delayedClose) {
            channelFuture.addListener(future -> close());
        }
        return channelFuture;
    }

    // internal use only, do not expose!
    @NonNull MutableCommonCounters counters() {
        return commonCounters;
    }

    @Override
    public Session toManagementSession() {
        final var builder = new SessionBuilder(commonCounters)
            .setSessionId(sessionId().getValue());

        final var remoteAddress = channel.remoteAddress();
        switch (remoteAddress) {
            case InetSocketAddress inet -> {
                // Note: In case this session was created via a connect-server, depending on how the connect address was
                //       specified, remoteAddress.getHostName() will be readily available and it might be more user
                //       friendly to report those.
                //
                //       On the other hand:
                //       - YANG model for domain-name is something of a best-effort, and is capped at 253 characters.
                //       - String storage is more wasteful
                //       - in a mixed listen/connect server, that would lead to different sessions being reported
                //         differently
                //       - getting to that information is quite fraught -- the best tool for this particular job is:
                //         IetfInetUtil.hostFor(inet.getHostString()), but that can fail as well.
                //
                //       So here we are consistently reporting using the InetAddress, side-stepping any possible bugs.
                builder.setSourceHost(new Host(IetfInetUtil.ipAddressFor(inet.getAddress())));
            }
            default -> {
                LOG.debug("Skipping unrecognized source host {}", remoteAddress);
            }
        }

        final String formattedDateTime = DATE_FORMATTER.format(loginTime);
        checkState(DATE_TIME_PATTERN.matcher(formattedDateTime).matches(),
            "Formatted datetime %s does not match pattern %s", formattedDateTime, DATE_TIME_PATTERN);

        return builder
                .setLoginTime(new DateAndTime(formattedDateTime))
                // FIXME: a TransportUser from somewhere around TransportChannel
                .setUsername(header.getUserName())
                // FIXME: derive from TransportChannel instead
                .setTransport(getTransportForString(header.getTransport()))
                // FIXME: obsolete this leaf and do not produce it here
                .addAugmentation(new Session1Builder().setSessionIdentifier(header.getSessionIdentifier()).build())
                .build();
    }

    private static Transport getTransportForString(final String transport) {
        return switch (transport) {
            case "ssh" -> NetconfSsh.VALUE;
            case "tcp" -> NetconfTcp.VALUE;
            default -> throw new IllegalArgumentException("Unknown transport type " + transport);
        };
    }

    @Override
    protected NetconfServerSession thisInstance() {
        return this;
    }

    @Override
    protected void addExiHandlers(final MessageDecoder decoder, final MessageWriter encoder) {
        replaceMessageDecoder(decoder);
        setMessageWriterAfterNextMessage(encoder);
    }

    @Override
    public void stopExiCommunication() {
        replaceMessageDecoder(new XMLMessageDecoder());
        setMessageWriterAfterNextMessage(XMLMessageWriter.pretty());
    }
}
