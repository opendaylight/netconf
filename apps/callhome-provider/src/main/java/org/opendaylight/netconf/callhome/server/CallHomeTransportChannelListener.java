/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.ClientChannelInitializer;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeTransportChannelListener implements TransportChannelListener {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeTransportChannelListener.class);

    private final @NonNull NetconfClientSessionNegotiatorFactory negotiationFactory;
    private final CallHomeSessionContextManager<?> contextManager;
    private final CallHomeStatusRecorder statusRecorder;

    public CallHomeTransportChannelListener(final NetconfClientSessionNegotiatorFactory negotiationFactory,
            final CallHomeSessionContextManager<?> contextManager, final CallHomeStatusRecorder statusRecorder) {
        this.negotiationFactory = requireNonNull(negotiationFactory);
        this.contextManager = requireNonNull(contextManager);
        this.statusRecorder = requireNonNull(statusRecorder);
    }

    @Override
    public void onTransportChannelEstablished(final TransportChannel transportChannel) {
        final var channel = transportChannel.channel();

        // identify or create session context associated with current connection
        final var context = contextManager.findByChannel(channel);
        if (context == null) {
            LOG.error("No valid context found for incoming connection from {}. Connection rejected.",
                channel.remoteAddress());
            channel.close();
            return;
        }

        LOG.info("Starting netconf negotiation for context: {}", context);

        // init NETCONF negotiation
        final var promise = channel.eventLoop().<NetconfClientSession>newPromise();
        promise.addListener(ignored -> {
            final var cause = promise.cause();
            if (cause != null) {
                contextManager.remove(context.id());
                context.settableFuture().setException(cause);
                statusRecorder.reportNetconfFailure(context.id());
                LOG.error("Netconf session failed for context: {}", context, cause);
            } else {
                statusRecorder.reportSuccess(context.id());
                context.settableFuture().set(promise.getNow());
                LOG.info("Netconf session established for context: {}", context);
            }
        });
        new ClientChannelInitializer(negotiationFactory, context::netconfSessionListener)
            .initialize(channel, promise);

        // this is required to trigger NETCONF negotiation on TLS
        if (channel.pipeline().get(SslHandler.class) != null) {
            channel.pipeline().fireChannelActive();
        }
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        statusRecorder.onTransportChannelFailure(cause);
    }
}
