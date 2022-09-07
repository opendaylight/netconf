/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev220718.TlsClientGrouping;

/**
 * A {@link TransportStack} acting as a TLS client.
 */
public final class TLSClient extends TLSTransportStack {
    private TLSClient(final TCPTransportStack tcpStack, final SslHandlerFactory sslFactory) {
        super(tcpStack, sslFactory);
    }

    public static @NonNull ListenableFuture<TLSClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    public static @NonNull ListenableFuture<TLSClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final TlsClientGrouping clientParams)
                throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }
}