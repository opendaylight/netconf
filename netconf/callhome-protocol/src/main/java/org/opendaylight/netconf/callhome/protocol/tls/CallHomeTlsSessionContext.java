/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.callhome.protocol.TransportType;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CallHomeTlsSessionContext implements CallHomeProtocolSessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeTlsSessionContext.class);

    private final SslConfigurationProvider sslConfigurationProvider;
    private final CallHomeNetconfSubsystemListener subsystemListener;

    private volatile boolean activated;
    private final TransportType transportType;
    private final String channelId;
    private SocketAddress socketAddress;

    public CallHomeTlsSessionContext(final String channelId, final SslConfigurationProvider sslConfigurationProvider,
                                     CallHomeNetconfSubsystemListener subsystemListener) {
        this.channelId = requireNonNull(channelId, "channelId");
        this.sslConfigurationProvider = requireNonNull(sslConfigurationProvider, "sslConfigurationProvider");
        this.subsystemListener = subsystemListener;
        this.transportType = TransportType.TLS;
    }

    public void openNetconfChannel(Channel ch) {
        this.socketAddress = ch.remoteAddress();
        LOG.debug("Opening NETCONF Subsystem on TLS connection {}", channelId);
        ch.remoteAddress();
        subsystemListener.onNetconfSubsystemOpened(this,
            listener -> doActivate(ch, listener));
    }

    @Override
    public void terminate() {
        //sshSession.close(false);
        removeSelf();
    }

    private void channelOpenFailed(final Throwable throwable) {
        LOG.error("Unable to open netconf subsystem, disconnecting.", throwable);
        //sshSession.close(false);
    }

    private synchronized Promise<NetconfClientSession> doActivate(final Channel channel,
            final NetconfClientSessionListener listener) {
        if (activated) {
            return newSessionPromise().setFailure(new IllegalStateException("Session (channel) already activated."));
        }

        activated = true;
        LOG.info("Activating Netconf channel for {} with {}", getRemoteAddress(), listener);
        Promise<NetconfClientSession> activationPromise = newSessionPromise();
        NetconfClientSessionNegotiatorFactory negotiatorFactory = new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
            Optional.empty(), TimeUnit.SECONDS.toMillis(5));
        TlsClientChannelInitializer tlsClientChannelInitializer = new TlsClientChannelInitializer(sslConfigurationProvider,
            negotiatorFactory, listener);
        tlsClientChannelInitializer.initialize(channel, activationPromise);
        return activationPromise;
    }

    private static Promise<NetconfClientSession> newSessionPromise() {
        return GlobalEventExecutor.INSTANCE.newPromise();
    }

    @Override
    public PublicKey getRemoteServerKey() {
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.socketAddress;
        //return remoteAddress;
    }

    @Override
    public String getSessionId() {
        return channelId;
        //return authorization.getSessionName();
    }

    void removeSelf() {
        //factory.remove(this);
    }

    public TransportType getTransportType() {
        return TransportType.TLS;
    }
}
