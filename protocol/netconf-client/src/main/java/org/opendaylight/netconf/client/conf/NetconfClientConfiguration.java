/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.List;
import org.checkerframework.checker.index.qual.NonNegative;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tls.SslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev240208.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev240208.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.TlsClientGrouping;

public final class NetconfClientConfiguration {
    public enum NetconfClientProtocol {
        TCP, SSH, TLS
    }

    private final @NonNull NetconfClientProtocol clientProtocol;
    private final Long connectionTimeoutMillis;
    private final NetconfClientSessionListener sessionListener;
    private final List<Uri> odlHelloCapabilities;
    private final @NonNegative int maximumIncomingChunkSize;
    private final String name;

    private final TcpClientGrouping tcpParameters;
    private final TlsClientGrouping tlsParameters;
    private final SslHandlerFactory sslHandlerFactory;
    private final SshClientGrouping sshParameters;
    private final ClientFactoryManagerConfigurator sshConfigurator;

    NetconfClientConfiguration(final NetconfClientProtocol protocol,
            final TcpClientGrouping tcpParameters,
            final TlsClientGrouping tlsParameters,
            final SslHandlerFactory sslHandlerFactory,
            final SshClientGrouping sshParameters,
            final ClientFactoryManagerConfigurator sshConfigurator,
            final NetconfClientSessionListener sessionListener,
            final List<Uri> odlHelloCapabilities,
            final Long connectionTimeoutMillis,
            final @NonNegative int maximumIncomingChunkSize,
            final String name) {
        clientProtocol = requireNonNull(protocol);
        this.name = name;
        this.tcpParameters = requireNonNull(tcpParameters);
        this.tlsParameters = tlsParameters;
        this.sslHandlerFactory = sslHandlerFactory;
        this.sshParameters = sshParameters;
        this.sshConfigurator = sshConfigurator;
        this.sessionListener = requireNonNull(sessionListener);
        this.odlHelloCapabilities = odlHelloCapabilities;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        // validate
        if (NetconfClientProtocol.TLS.equals(protocol)) {
            Preconditions.checkArgument(tlsParameters != null || sslHandlerFactory != null,
                "Either tlsParameters or sslHandlerFactory is required");
        } else if (NetconfClientProtocol.SSH.equals(protocol)) {
            requireNonNull(sshParameters);
        }
    }

    public String getName() {
        return name;
    }

    public Long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public NetconfClientSessionListener getSessionListener() {
        return sessionListener;
    }

    public @NonNull NetconfClientProtocol getProtocol() {
        return clientProtocol;
    }

    public SslHandlerFactory getSslHandlerFactory() {
        return sslHandlerFactory;
    }

    public List<Uri> getOdlHelloCapabilities() {
        return odlHelloCapabilities;
    }

    public @NonNegative int getMaximumIncomingChunkSize() {
        return maximumIncomingChunkSize;
    }

    public TcpClientGrouping getTcpParameters() {
        return tcpParameters;
    }

    public TlsClientGrouping getTlsParameters() {
        return tlsParameters;
    }

    public SshClientGrouping getSshParameters() {
        return sshParameters;
    }

    public ClientFactoryManagerConfigurator getSshConfigurator() {
        return sshConfigurator;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("clientProtocol", clientProtocol)
            .add("connectionTimeoutMillis", connectionTimeoutMillis)
            .add("sessionListener", sessionListener)
            .add("tcpParameters", tcpParameters)
            .add("tlsParameters", tlsParameters)
            .add("sshParameters", sshParameters)
            .add("sslHandlerFactory (defined)", sslHandlerFactory != null)
            .add("sslHandlerFactory (defined)", sshConfigurator != null)
            .toString();
    }
}
