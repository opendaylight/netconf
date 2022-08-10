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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.AbstractTransportStackBootstrap;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev220718.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.concepts.Mutable;

/**
 * A bootstrap allowing instantiation of {@link SSHTransportStack}s.
 */
public abstract sealed class SSHTransportStackBootstrap extends AbstractTransportStackBootstrap {
    private static final class Client extends SSHTransportStackBootstrap {
        private final SshClientGrouping parameters;

        private Client(final TransportChannelListener listener, final SshClientGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
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
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
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

    SSHTransportStackBootstrap(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull ClientBuilder clientBuilder() {
        return new ClientBuilder();
    }

    @Override
    public final CompletionStage<TransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass
        final var remoteHost = require(connectParams, TcpClientGrouping::getRemoteAddress, "remote-address");
        final var remotePort = require(connectParams, TcpClientGrouping::getRemotePort, "remote-port");
        final var remoteAddr = remoteHost.getIpAddress();

        // FIXME: server + initiate = remote 4334
        // FIXME: server + listen   = local   830

        return initiate(addressOf(connectParams.getLocalAddress(), connectParams.getLocalPort(), 0),
            remoteAddr != null ? addressOf(remoteAddr, remotePort, 830)
                : new SshdSocketAddress(
                    require(remoteHost, Host::getDomainName, "remote-address/domain-name").getValue(),
                    portNumber(remotePort, 830)));
    }

    abstract @NonNull CompletionStage<TransportStack> initiate(SshdSocketAddress local, SshdSocketAddress remote)
        throws UnsupportedConfigurationException;

    @Override
    public final CompletionStage<TransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass

        // FIXME: client + initiate = remote  830
        // FIXME: client + listen   = local  4334

        return listen(addressOf(require(listenParams, TcpServerGrouping::getLocalAddress, "local-address"),
            listenParams.getLocalPort(), 4334));
    }

    abstract @NonNull CompletionStage<TransportStack> listen(SshdSocketAddress local)
        throws UnsupportedConfigurationException;

    private static SshdSocketAddress addressOf(final @Nullable IpAddress address,
            final @Nullable PortNumber port, final int defaultPort) {
        final int portNum = portNumber(port, defaultPort);
        return address == null ?  new SshdSocketAddress(portNum)
            : new SshdSocketAddress(new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(address), portNum));
    }
}
