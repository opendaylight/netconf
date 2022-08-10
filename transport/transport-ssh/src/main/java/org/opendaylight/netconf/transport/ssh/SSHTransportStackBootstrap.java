/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.AbstractTransportStackBootstrap;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd5;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd596;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha196;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.MacAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.TransportParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.KeyExchange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Mac;
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
        protected int tcpConnectPort() {
            return NETCONF_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return CALLHOME_PORT;
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
        protected int tcpConnectPort() {
            return CALLHOME_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return NETCONF_PORT;
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

    private static final int NETCONF_PORT = 830;
    private static final int CALLHOME_PORT = 4334;

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

        return initiate(addressOf(connectParams.getLocalAddress(), connectParams.getLocalPort(), 0),
            remoteAddr != null ? addressOf(remoteAddr, remotePort, tcpConnectPort())
                : new SshdSocketAddress(
                    require(remoteHost, Host::getDomainName, "remote-address/domain-name").getValue(),
                    portNumber(remotePort, tcpConnectPort())));
    }

    abstract @NonNull CompletionStage<TransportStack> initiate(SshdSocketAddress local, SshdSocketAddress remote)
        throws UnsupportedConfigurationException;

    @Override
    public final CompletionStage<TransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass

        return listen(addressOf(require(listenParams, TcpServerGrouping::getLocalAddress, "local-address"),
            listenParams.getLocalPort(), tcpListenPort()));
    }

    abstract @NonNull CompletionStage<TransportStack> listen(SshdSocketAddress local)
        throws UnsupportedConfigurationException;

    private static SshdSocketAddress addressOf(final @Nullable IpAddress address,
            final @Nullable PortNumber port, final int defaultPort) {
        final int portNum = portNumber(port, defaultPort);
        return address == null ?  new SshdSocketAddress(portNum)
            : new SshdSocketAddress(new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(address), portNum));
    }

    private static void setTransportParams(final @NonNull BaseBuilder<?, ?> builder,
            final @Nullable TransportParamsGrouping params) throws UnsupportedConfigurationException {
        if (params != null) {
            setEncryption(builder, params.getEncryption());
            setHostKey(builder, params.getHostKey());
            setKeyExchange(builder, params.getKeyExchange());
            setMac(builder, params.getMac());
        }
    }

    private static void setEncryption(final BaseBuilder<?, ?> baseBuilder, final Encryption encryption)
            throws UnsupportedConfigurationException {
        if (encryption == null) {
            return;
        }

        // FIXME: implement this
    }

    private static void setHostKey(final BaseBuilder<?, ?> baseBuilder, final HostKey hostKey)
            throws UnsupportedConfigurationException {
        if (hostKey == null) {
            return;
        }

        // FIXME: implement this
    }

    private static void setKeyExchange(final BaseBuilder<?, ?> baseBuilder, final KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        if (keyExchange == null) {
            return;
        }

        // FIXME: implement this
    }

    private static void setMac(final BaseBuilder<?, ?> baseBuilder, final Mac mac)
            throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null) {
                baseBuilder.macFactories(createMacFactories(macAlg));
                return;
            }
        }
        // FIXME: set defaults?
    }

    private static List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> createMacFactories(
            final List<MacAlgBase> macAlg) throws UnsupportedConfigurationException {
        if (macAlg.isEmpty()) {
            throw new UnsupportedConfigurationException("mac-alg is empty");
        }

        // FIXME: cache these
        final var builder = ImmutableList.<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>>
            builderWithExpectedSize(macAlg.size());
        for (var alg : macAlg) {
            builder.add(macFactoryOf(alg));
        }
        return builder.build();
    }

    private static @NonNull MacFactory macFactoryOf(final MacAlgBase alg) throws UnsupportedConfigurationException {
        // FIXME: resolve by name?!
        if (HmacSha2512.VALUE.equals(alg)) {
            return BuiltinMacs.hmacsha512;
        } else if (HmacSha2256.VALUE.equals(alg)) {
            return BuiltinMacs.hmacsha256;
        } else if (HmacSha1.VALUE.equals(alg)) {
            return BuiltinMacs.hmacsha1;
        } else if (HmacSha196.VALUE.equals(alg)) {
            return BuiltinMacs.hmacsha196;
        } else if (HmacMd5.VALUE.equals(alg)) {
            return BuiltinMacs.hmacmd5;
        } else if (HmacMd596.VALUE.equals(alg)) {
            return BuiltinMacs.hmacmd596;
        } else {
            // FIXME: AeadAes128Gcm.VALUE
            // FIXME: AeadAes256Gcm.VALUE
            // FIXME: None.VALUE
            // FIXME openssh ETM extensions
            throw new UnsupportedConfigurationException("Unsupported MAC algorithm " + alg);
        }
    }
}
