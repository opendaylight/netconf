/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.Future;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
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
    }

    private static final class Server extends SSHTransportStackBootstrap {
        private final SshServerGrouping parameters;

        private Server(final TransportChannelListener listener, final SshServerGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
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

    public final @NonNull Future<SSHTransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    public final @NonNull Future<SSHTransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
