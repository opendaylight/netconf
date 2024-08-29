/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.NetconfChannel;
import org.opendaylight.netconf.nettyutil.NetconfChannelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeTransportChannelListener extends NetconfChannelListener {
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
    protected void onNetconfChannelEstablished(final NetconfChannel channel) {
        final var nettyChannel = channel.transport().channel();

        // identify or create session context associated with current connection
        final var context = contextManager.findByChannel(nettyChannel);
        if (context == null) {
            LOG.error("No valid context found for incoming connection from {}. Connection rejected.",
                nettyChannel.remoteAddress());
            nettyChannel.close();
            return;
        }

        LOG.info("Starting netconf negotiation for context: {}", context);

        // init NETCONF negotiation
        final var promise = nettyChannel.eventLoop().<NetconfClientSession>newPromise();
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

        final var pipeline = nettyChannel.pipeline()
            .addLast(NETCONF_SESSION_NEGOTIATOR,
                negotiationFactory.getSessionNegotiator(context.netconfSessionListener(), nettyChannel, promise));
        nettyChannel.config().setConnectTimeoutMillis((int) negotiationFactory.getConnectionTimeoutMillis());

        // this is required to trigger NETCONF negotiation on TLS
        if (pipeline.get(SslHandler.class) != null) {
            pipeline.fireChannelActive();
        }
    }

    @Override
    public void onTransportChannelFailed(final Throwable cause) {
        statusRecorder.onTransportChannelFailure(cause);
    }
}
