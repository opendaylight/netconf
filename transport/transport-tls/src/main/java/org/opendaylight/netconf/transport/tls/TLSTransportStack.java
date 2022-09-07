/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.ssl.SslHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Base class for TLS TransportStacks.
 */
public abstract sealed class TLSTransportStack extends AbstractTransportStack permits TLSClient, TLSServer {
    private final @NonNull TCPTransportStack tcpStack;

    private volatile @NonNull SSLEngineFactory engineFactory;

    TLSTransportStack(final TCPTransportStack tcpStack, final SSLEngineFactory engineFactory) {
        this.tcpStack = requireNonNull(tcpStack);
        this.engineFactory = requireNonNull(engineFactory);
    }

    @Override
    protected final ListenableFuture<Empty> startShutdown() {
        return tcpStack.shutdown();
    }

    final void setEngineFactory(final SSLEngineFactory engineFactory) {
        this.engineFactory = requireNonNull(engineFactory);
    }

    final @NonNull SslHandler newSslHandler() {
        final var sslEngine = engineFactory.newSSLEngine();
        sslEngine.setUseClientMode(useClientMode());
        return new SslHandler(sslEngine);
    }

    abstract boolean useClientMode();
}
