/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.client.TlsClientChannelInitializer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev231025.connection.parameters.Protocol.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CallHomeTlsSessionContext implements CallHomeProtocolSessionContext {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeTlsSessionContext.class);

    private final AtomicBoolean activated = new AtomicBoolean();
    private final SslHandlerFactory sslHandlerFactory;
    private final CallHomeNetconfSubsystemListener subsystemListener;
    private final String deviceId;
    private final Channel channel;
    private final PublicKey publicKey;
    private final SocketAddress socketAddress;

    CallHomeTlsSessionContext(final String deviceId, final Channel channel, final SslHandlerFactory sslHandlerFactory,
                              final CallHomeNetconfSubsystemListener subsystemListener) {
        this.channel = requireNonNull(channel, "channel");
        this.deviceId = deviceId;
        socketAddress = channel.remoteAddress();
        publicKey = createPublicKey(channel);
        this.sslHandlerFactory = requireNonNull(sslHandlerFactory, "sslHandlerFactory");
        this.subsystemListener = subsystemListener;
    }

    private static Promise<NetconfClientSession> newSessionPromise() {
        return GlobalEventExecutor.INSTANCE.newPromise();
    }

    void openNetconfChannel(final Channel ch) {
        LOG.debug("Opening NETCONF Subsystem on TLS connection {}", deviceId);
        subsystemListener.onNetconfSubsystemOpened(this, listener -> doActivate(ch, listener));
    }

    @Override
    public void terminate() {
        channel.close();
    }

    private ListenableFuture<NetconfClientSession> doActivate(final Channel ch,
            final NetconfClientSessionListener listener) {
        final Promise<NetconfClientSession> activationPromise = newSessionPromise();
        if (activated.compareAndExchange(false, true)) {
            return Futures.immediateFailedFuture(new IllegalStateException("Session (channel) already activated."));
        }

        LOG.info("Activating Netconf channel for {} with {}", getRemoteAddress(), listener);
        final NetconfClientSessionNegotiatorFactory negotiatorFactory = new NetconfClientSessionNegotiatorFactory(
            new HashedWheelTimer(), Optional.empty(), TimeUnit.SECONDS.toMillis(5));
        final TlsClientChannelInitializer tlsClientChannelInitializer = new TlsClientChannelInitializer(
            sslHandlerFactory, negotiatorFactory, listener);
        tlsClientChannelInitializer.initialize(ch, activationPromise);
        final SettableFuture<NetconfClientSession> future = SettableFuture.create();
        activationPromise.addListener(ignored -> {
            final var cause = activationPromise.cause();
            if (cause != null) {
                future.setException(cause);
            } else {
                future.set(activationPromise.getNow());
            }
        });
        return future;
    }

    @Override
    public PublicKey getRemoteServerKey() {
        return publicKey;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return socketAddress;
    }

    @Override
    public String getSessionId() {
        return deviceId;
    }

    @Override
    public Name getTransportType() {
        return Name.TLS;
    }

    private static PublicKey createPublicKey(final Channel ch) {
        final SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
        final Certificate[] certificates;
        try {
            certificates = sslHandler.engine().getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            LOG.error("Peer certificate was not established during the handshake", e);
            throw new IllegalStateException("No peer certificate present", e);
        }
        return certificates[0].getPublicKey();
    }
}
