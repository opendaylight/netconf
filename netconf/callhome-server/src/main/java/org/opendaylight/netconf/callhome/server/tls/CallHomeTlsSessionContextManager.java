/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;
import java.security.PublicKey;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.opendaylight.netconf.callhome.server.AbstractCallHomeSessionContextManager;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeTlsSessionContextManager extends AbstractCallHomeSessionContextManager<CallHomeTlsSessionContext> {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeTlsSessionContextManager.class);

    private final CallHomeTlsAuthProvider authProvider;
    private final CallHomeStatusRecorder statusRecorder;

    public CallHomeTlsSessionContextManager(final CallHomeTlsAuthProvider authProvider,
        final CallHomeStatusRecorder statusRecorder) {
        super();
        this.authProvider = requireNonNull(authProvider);
        this.statusRecorder = requireNonNull(statusRecorder);
    }

    @Override
    public CallHomeTlsSessionContext findByChannel(final Channel channel) {
        requireNonNull(channel);
        return channel.isOpen() ? createValidContext(channel) : null;
    }

    private CallHomeTlsSessionContext createValidContext(final Channel channel) {
        // extract peer public key from SSL session
        final PublicKey publicKey;
        try {
            final var cert = channel.pipeline().get(SslHandler.class).engine().getSession()
                .getPeerCertificates()[0];
            publicKey = cert.getPublicKey();
        } catch (SSLPeerUnverifiedException e) {
            LOG.error("Exception retrieving certificate", e);
            return null;
        }
        // identify connection
        final String id = authProvider.idFor(publicKey);
        if (id == null) {
            statusRecorder.reportUnknown(channel.remoteAddress(), publicKey);
            return null;
        }

        final var context = createContext(id, channel);
        if (context == null) {
            // if there is an issue creating context then the cause expected to be
            // logged within overridden createContext() method
            return null;
        }
        register(context);
        // close callback
        channel.closeFuture().addListener(ignored -> {
            remove(id);
            if (context.settableFuture().isDone()) {
                // disconnect after netconf session established
                statusRecorder.reportDisconnected(id);
            }
        });

        LOG.debug("Session context is created for TLS session: {}", context);
        return context;
    }

    /**
     * Builds {@link CallHomeTlsSessionContext} based on {@link Channel} object.
     *
     * <p> The method expected to be overridden in order to inject
     * {@link org.opendaylight.netconf.client.NetconfClientSessionListener NetconfClientSessionListener} and/or
     * {@link SettableFuture} of a {@link org.opendaylight.netconf.client.NetconfClientSession NetconfClientSession}
     *  to be established on current connection.
     *
     * @param id unique client (device) identifier
     * @param channel netty channel instance
     * @return created object or {@code null} if it cannot be created for some reason
     */
    public CallHomeTlsSessionContext createContext(final String id, final Channel channel) {
        return new CallHomeTlsSessionContext(id, channel, new SimpleNetconfClientSessionListener(),
            SettableFuture.create());
    }
}
