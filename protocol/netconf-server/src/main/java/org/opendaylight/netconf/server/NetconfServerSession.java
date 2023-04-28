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

import com.google.common.net.InetAddresses;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSession;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.server.api.monitoring.NetconfManagementSession;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter32;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.Session1Builder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfServerSession extends AbstractNetconfSession<NetconfServerSession,
        NetconfServerSessionListener> implements NetconfManagementSession {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSession.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_TIME_PATTERN;

    static {
        verify(DateAndTime.PATTERN_CONSTANTS.size() == 1);
        DATE_TIME_PATTERN = Pattern.compile(DateAndTime.PATTERN_CONSTANTS.get(0));
    }

    private final NetconfHelloMessageAdditionalHeader header;
    private final NetconfServerSessionListener sessionListener;

    private ZonedDateTime loginTime;
    private long inRpcSuccess;
    private long inRpcFail;
    private long outRpcError;
    private long outNotification;
    private volatile boolean delayedClose;

    public NetconfServerSession(final NetconfServerSessionListener sessionListener, final Channel channel,
                                final SessionIdType sessionId, final NetconfHelloMessageAdditionalHeader header) {
        super(sessionListener, channel, sessionId);
        this.header = header;
        this.sessionListener = sessionListener;
        LOG.debug("Session {} created", this);
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
            outNotification++;
            sessionListener.onNotification(this, notification);
        }
        // delayed close was set, close after the message was sent
        if (delayedClose) {
            channelFuture.addListener(future -> close());
        }
        return channelFuture;
    }

    public void onIncommingRpcSuccess() {
        inRpcSuccess++;
    }

    public void onIncommingRpcFail() {
        inRpcFail++;
    }

    public void onOutgoingRpcError() {
        outRpcError++;
    }

    @Override
    public Session toManagementSession() {
        final SessionBuilder builder = new SessionBuilder().withKey(new SessionKey(sessionId().getValue()));
        final InetAddress address1 = InetAddresses.forString(header.getAddress());
        final IpAddress address;
        if (address1 instanceof Inet4Address) {
            address = new IpAddress(new Ipv4Address(header.getAddress()));
        } else {
            address = new IpAddress(new Ipv6Address(header.getAddress()));
        }
        builder.setSourceHost(new Host(address));

        final String formattedDateTime = DATE_FORMATTER.format(loginTime);
        checkState(DATE_TIME_PATTERN.matcher(formattedDateTime).matches(),
            "Formatted datetime %s does not match pattern %s", formattedDateTime, DATE_TIME_PATTERN);

        return builder
                .setLoginTime(new DateAndTime(formattedDateTime))
                .setInBadRpcs(new ZeroBasedCounter32(Uint32.valueOf(inRpcFail)))
                .setInRpcs(new ZeroBasedCounter32(Uint32.valueOf(inRpcSuccess)))
                .setOutRpcErrors(new ZeroBasedCounter32(Uint32.valueOf(outRpcError)))
                .setUsername(header.getUserName())
                .setTransport(getTransportForString(header.getTransport()))
                .setOutNotifications(new ZeroBasedCounter32(Uint32.valueOf(outNotification)))
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
    protected void addExiHandlers(final ByteToMessageDecoder decoder,
                                  final MessageToByteEncoder<NetconfMessage> encoder) {
        replaceMessageDecoder(decoder);
        replaceMessageEncoderAfterNextMessage(encoder);
    }

    @Override
    public void stopExiCommunication() {
        replaceMessageDecoder(new NetconfXMLToMessageDecoder());
        replaceMessageEncoderAfterNextMessage(new NetconfMessageToXMLEncoder());
    }
}
