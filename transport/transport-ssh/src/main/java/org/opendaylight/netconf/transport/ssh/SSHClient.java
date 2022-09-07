/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfClientBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev221212.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev221212.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev221212.local.or.truststore._public.keys.grouping.local.or.truststore.Local;

/**
 * A {@link TransportStack} acting as an SSH client.
 */
public final class SSHClient extends SSHTransportStack {
    private SSHClient(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull ListenableFuture<SSHClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        final var client = newClient(clientParams);

        final var future = client.connect("",
            newAddress(connectParams.requireRemoteAddress(), connectParams.requireRemotePort()),
            newAddress(connectParams.getLocalAddress(), connectParams.getLocalPort()));

        return transformUnderlay(client, TCPClient.connect(client.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SshClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        final var client = newClient(clientParams);

        return transformUnderlay(client, TCPServer.listen(client.asListener(), bootstrap, listenParams));
    }

    private static InetSocketAddress newAddress(final IpAddress ip, final PortNumber port) {
        return new InetSocketAddress(ip != null ? IetfInetUtil.INSTANCE.inetAddressFor(ip) : null,
            port != null ? port.getValue().toJava() : 0);
    }

    private static @NonNull SocketAddress newAddress(final Host host, final PortNumber port)
            throws UnsupportedConfigurationException {
        final var portNum = port.getValue().toJava();
        final var ipAddress = host.getIpAddress();
        if (ipAddress != null) {
            return new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(ipAddress), portNum);
        }
        final var name = host.getDomainName();
        if (name != null) {
            return new SshdSocketAddress(name.getValue(), portNum);
        }

        throw new UnsupportedConfigurationException("Unsupported host " + host);
    }

    private static NetconfSshClient newClient(final SshClientGrouping parameters)
            throws UnsupportedConfigurationException {
        final var builder = new NetconfClientBuilder();

        final var clientIdentity = parameters.getClientIdentity();
        if (clientIdentity != null) {
            // FIXME: set identity
        }
        final var serverAuthentication = parameters.getServerAuthentication();
        if (serverAuthentication != null) {
            final var hostKeys = serverAuthentication.getSshHostKeys();
            if (hostKeys != null) {
                final var ref = hostKeys.getLocalOrTruststore();
                if (ref instanceof Local local) {
                    final var localDef = local.getLocalDefinition();
                    if (localDef != null) {
                        final var publicKeys = localDef.nonnullPublicKey();
                        // FIXME: implement this
                        // builder.serverKeyVerifier()
                    }
                } else {
                    throw new UnsupportedConfigurationException("Unsuppoerted ssh-host-keys " + hostKeys);
                }
            }
        }
        setTransportParams(builder, CLIENT_KEXS, parameters.getTransportParams());

        final var client = builder.build();

        final var keepAlives = parameters.getKeepalives();
        if (keepAlives != null) {
            final var attempts = keepAlives.requireMaxAttempts().toJava();
            final var wait = keepAlives.requireMaxWait().toJava();
            // HEARTBEAT_INTERVAL + inactivity timer

            // FIXME: implement this
        }

        return client;
    }
}