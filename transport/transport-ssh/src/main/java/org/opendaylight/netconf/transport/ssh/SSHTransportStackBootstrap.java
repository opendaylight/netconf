/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev220718.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.Mutable;

/**
 * A bootstrap allowing instantiation of {@link SSHTransportStack}s.
 */
public abstract sealed class SSHTransportStackBootstrap implements Immutable {
    private static final class Client extends SSHTransportStackBootstrap {
        private final SshClientGrouping parameters;

        private Client(final TransportChannelListener listener, final SshClientGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        CompletionStage<SSHTransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<SSHTransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    private static final class Server extends SSHTransportStackBootstrap {
        private final SshServerGrouping parameters;

        private Server(final TransportChannelListener listener, final SshServerGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        CompletionStage<SSHTransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<SSHTransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    public static final class ClientBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshClientGrouping parameters;

        private ClientBuilder() {
            // Hidden on purpose
        }

        public @NonNull ClientBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ClientBuilder setParameters(final SshClientGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Client(listener, parameters);
        }
    }

    public static final class ServerBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshServerGrouping parameters;

        private ServerBuilder() {
            // Hidden on purpose
        }

        public @NonNull ServerBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ServerBuilder setParameters(final SshServerGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Server(listener, parameters);
        }
    }

    private final TransportChannelListener listener;

    SSHTransportStackBootstrap(final TransportChannelListener listener) {
        this.listener = requireNonNull(listener);
    }

    public static @NonNull ClientBuilder clientBuilder() {
        return new ClientBuilder();
    }

    public final @NonNull CompletionStage<SSHTransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        final var remoteHost = require(connectParams, TcpClientGrouping::getRemoteAddress, "remote-address");
        final var remotePort = require(connectParams, TcpClientGrouping::getRemotePort, "remote-port");
        final SshdSocketAddress connectAddr;
        final var remoteAddr = remoteHost.getIpAddress();
        if (remoteAddr == null) {
            final var remoteIp = require(remoteHost, Host::getDomainName, "remote-address/domain-name");
            connectAddr = new SshdSocketAddress(remoteIp.getValue(), portNumber(remotePort, 830));
        } else {
            connectAddr = addressOf(remoteAddr, remotePort, 830);
        }

        return initiate(addressOf(connectParams.getLocalAddress(), connectParams.getLocalPort(), 0), connectAddr);
    }

    abstract @NonNull CompletionStage<SSHTransportStack> initiate(SshdSocketAddress local, SshdSocketAddress remote)
        throws UnsupportedConfigurationException;

    public final @NonNull CompletionStage<SSHTransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        return listen(addressOf(require(listenParams, TcpServerGrouping::getLocalAddress, "local-address"),
            listenParams.getLocalPort(), 4334));
    }

    abstract @NonNull CompletionStage<SSHTransportStack> listen(SshdSocketAddress local)
        throws UnsupportedConfigurationException ;

    private static SshdSocketAddress addressOf(final @Nullable IpAddress address,
            final @Nullable PortNumber port, final int defaultPort) {
        final int portNum = portNumber(port, defaultPort);
        return address == null ?  new SshdSocketAddress(portNum)
            : new SshdSocketAddress(new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(address), portNum));
    }

    private static int portNumber(final @Nullable PortNumber port, final int defaultPort) {
        if (port != null) {
            final int portVal = port.getValue().toJava();
            if (portVal != 0) {
                return portVal;
            }
        }
        return defaultPort;
    }

    private static <O, T> @NonNull T require(final O obj, final Function<O, T> method, final String attribute)
            throws UnsupportedConfigurationException {
        final var ret = method.apply(obj);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Missing mandatory attribute " + attribute);
        }
        return ret;
    }
}
