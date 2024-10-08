/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.net.InetAddresses;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Additional header can be used with hello message to carry information about
 * session's connection. Provided information can be reported via netconf
 * monitoring.
 * <pre>
 * It has PATTERN "[username; host-address:port; transport; session-identifier;]"
 * username - name of account on a remote
 * host-address - client's IP address
 * port - port number
 * transport - tcp, ssh
 * session-identifier - persister, client
 * Session-identifier is optional, others mandatory.
 * </pre>
 * This header is inserted in front of a netconf hello message followed by a newline.
 *
 * @deprecated This class is a leftover from initial implementation where SSH was done by http://www.jcraft.com/jsch/
 *             and piped channels to get around the sync nature of JSch. We are now using a directly-connected Netty
 *             channel via transport-ssh.
 */
// FIXME: session-identifier is unused
// FIXME: hostAddress, and port is readily available from Channel
// FIXME: transport should be visible in the form of TransportChannel in NetconfServerSession, the actual String here
//        is a leak towards netconf-server-'s ietf-netconf-monitoring operational state. That piece of code should use
//        a switch expression to go from 'transportChannel instanceof SSHTransportChannel' to 'NetconfSsh.VALUE' without
//        the intermediate random string -- which does not support TLS anyway!
// FIXME: userName is coming from:
//        - authentication in case of SSH/TLS
//        - from wire in case of TCP
//        We should propagate these via transport-api:
//        -  sealed interface TransportUser permits AuthenticatedUser, UnauthenticatedUser
//        -  non-sealed interface AuthenticatedUser
//        -  final class UnauthenticatedUser
//        A TransportUser and TransportChannel via a netconf.codec.NetconfChannel exposed to
//        ServerSessionNegotiator. On ClientSessionNegotiator side we want to have TransportServer exposing server's
//        credentials, like the result of server key authentication.
@Deprecated(since = "8.0.0", forRemoval = true)
public class NetconfHelloMessageAdditionalHeader {

    private static final String SC = ";";

    private final String userName;
    private final String hostAddress;
    private final String port;
    private final String transport;
    private final String sessionIdentifier;

    public NetconfHelloMessageAdditionalHeader(final String userName, final String hostAddress, final String port,
                                               final String transport, final String sessionIdentifier) {
        this.userName = userName;
        this.hostAddress = hostAddress;
        this.port = port;
        this.transport = transport;
        this.sessionIdentifier = sessionIdentifier;
    }

    public String getUserName() {
        return userName;
    }

    public String getAddress() {
        return hostAddress;
    }

    public String getPort() {
        return port;
    }

    public String getTransport() {
        return transport;
    }

    public String getSessionIdentifier() {
        return sessionIdentifier;
    }

    /**
     * Format additional header into a string suitable as a prefix for netconf hello message.
     */
    public String toFormattedString() {
        requireNonNull(userName);
        requireNonNull(hostAddress);
        requireNonNull(port);
        requireNonNull(transport);
        requireNonNull(sessionIdentifier);
        return "[" + userName + SC + hostAddress + ":" + port + SC + transport + SC + sessionIdentifier + SC + "]"
                + System.lineSeparator();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NetconfHelloMessageAdditionalHeader{");
        sb.append("userName='").append(userName).append('\'');
        sb.append(", hostAddress='").append(hostAddress).append('\'');
        sb.append(", port='").append(port).append('\'');
        sb.append(", transport='").append(transport).append('\'');
        sb.append(", sessionIdentifier='").append(sessionIdentifier).append('\'');
        sb.append('}');
        return sb.toString();
    }

    private static final Pattern PATTERN = Pattern
            .compile("\\[(?<username>[^;]+);(?<address>.+)[:/](?<port>[0-9]+);(?<transport>[a-z]+)[^\\]]+\\]");
    private static final Pattern CUSTOM_HEADER_PATTERN = Pattern
            .compile("\\[(?<username>[^;]+);"
                    + "(?<address>.+)[:/](?<port>[0-9]+);(?<transport>[a-z]+);(?<sessionIdentifier>[a-z]+)[^\\]]+\\]");

    /**
     * Parse additional header from a formatted string.
     */
    public static NetconfHelloMessageAdditionalHeader fromString(final String additionalHeader) {
        String additionalHeaderTrimmed = additionalHeader.trim();
        Matcher matcher = PATTERN.matcher(additionalHeaderTrimmed);
        Matcher matcher2 = CUSTOM_HEADER_PATTERN.matcher(additionalHeaderTrimmed);
        checkArgument(matcher.matches(), "Additional header in wrong format %s, expected %s",
                additionalHeaderTrimmed, PATTERN);

        String username = matcher.group("username");
        String address = matcher.group("address");
        checkArgument(InetAddresses.isInetAddress(address));
        String port = matcher.group("port");
        String transport = matcher.group("transport");
        String sessionIdentifier = "client";
        if (matcher2.matches()) {
            sessionIdentifier = matcher2.group("sessionIdentifier");
        }
        return new NetconfHelloMessageAdditionalHeader(username, address, port, transport, sessionIdentifier);
    }
}
