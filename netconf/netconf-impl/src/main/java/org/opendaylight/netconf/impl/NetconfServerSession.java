/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import com.google.common.base.Preconditions;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.nettyutil.AbstractNetconfSession;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.NetconfTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.Session1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.Session1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter32;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfServerSession extends AbstractNetconfSession<NetconfServerSession,
        NetconfServerSessionListener> implements NetconfManagementSession {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSession.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String DATE_TIME_PATTERN_STRING = DateAndTime.PATTERN_CONSTANTS.get(0);
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(DATE_TIME_PATTERN_STRING);

    private final NetconfHelloMessageAdditionalHeader header;
    private final NetconfServerSessionListener sessionListener;

    private ZonedDateTime loginTime;
    private long inRpcSuccess;
    private long inRpcFail;
    private long outRpcError;
    private long outNotification;
    private volatile boolean delayedClose;

    public NetconfServerSession(final NetconfServerSessionListener sessionListener, final Channel channel,
                                final long sessionId, final NetconfHelloMessageAdditionalHeader header) {
        super(sessionListener, channel, sessionId);
        this.header = header;
        this.sessionListener = sessionListener;
        LOG.debug("Session {} created", toString());
    }

    @Override
    protected void sessionUp() {
        Preconditions.checkState(loginTime == null, "Session is already up");
        this.loginTime = Instant.now().atZone(ZoneId.systemDefault());
        super.sessionUp();
    }

    /**
     * Close this session after next message is sent.
     * Suitable for close rpc that needs to send ok response before the session is closed.
     */
    public void delayedClose() {
        this.delayedClose = true;
    }

    @Override
    public ChannelFuture sendMessage(final NetconfMessage netconfMessage) {
        final ChannelFuture channelFuture = super.sendMessage(netconfMessage);
        if (netconfMessage instanceof NetconfNotification) {
            outNotification++;
            sessionListener.onNotification(this, (NetconfNotification) netconfMessage);
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
        SessionBuilder builder = new SessionBuilder();

        builder.setSessionId(getSessionId());
        IpAddress address;
        InetAddress address1 = InetAddresses.forString(header.getAddress());
        if (address1 instanceof Inet4Address) {
            address = new IpAddress(new Ipv4Address(header.getAddress()));
        } else {
            address = new IpAddress(new Ipv6Address(header.getAddress()));
        }
        builder.setSourceHost(new Host(address));

        Preconditions.checkState(DateAndTime.PATTERN_CONSTANTS.size() == 1);
        String formattedDateTime = DATE_FORMATTER.format(loginTime);

        Matcher matcher = DATE_TIME_PATTERN.matcher(formattedDateTime);
        Preconditions.checkState(matcher.matches(), "Formatted datetime %s does not match pattern %s",
                formattedDateTime, DATE_TIME_PATTERN);
        builder.setLoginTime(new DateAndTime(formattedDateTime));

        builder.setInBadRpcs(new ZeroBasedCounter32(inRpcFail));
        builder.setInRpcs(new ZeroBasedCounter32(inRpcSuccess));
        builder.setOutRpcErrors(new ZeroBasedCounter32(outRpcError));

        builder.setUsername(header.getUserName());
        builder.setTransport(getTransportForString(header.getTransport()));

        builder.setOutNotifications(new ZeroBasedCounter32(outNotification));

        builder.withKey(new SessionKey(Uint32.valueOf(getSessionId())));

        Session1Builder builder1 = new Session1Builder();
        builder1.setSessionIdentifier(header.getSessionIdentifier());
        builder.addAugmentation(Session1.class, builder1.build());

        return builder.build();
    }

    private static Class<? extends Transport> getTransportForString(final String transport) {
        switch (transport) {
            case "ssh":
                return NetconfSsh.class;
            case "tcp":
                return NetconfTcp.class;
            default:
                throw new IllegalArgumentException("Unknown transport type " + transport);
        }
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
