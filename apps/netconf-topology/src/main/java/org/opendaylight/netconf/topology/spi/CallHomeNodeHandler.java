/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.concurrent.CancellationException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All state associated with a NETCONF topology node connected through call-home. Unlike {@link NetconfNodeHandler},
 * this handler does not connect to the device: it attaches to a transport which has already been accepted from the
 * device, using a {@link CallHomeSessionInitializer}.
 *
 * <p>The handler is one-shot. It is connected exactly once and does not attempt to reconnect when the session goes
 * down or fails: the device is expected to call home again, which results in a new handler. The handler is closed by
 * its owner when the accepted transport goes away.
 */
public final class CallHomeNodeHandler extends AbstractNodeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeNodeHandler.class);

    private final @NonNull CallHomeSessionInitializer sessionInitializer;

    @GuardedBy("this")
    private ListenableFuture<NetconfClientSession> sessionFuture;

    /**
     * Construct a new handler.
     *
     * @param timer timer used for keepalives and RPC timeouts
     * @param baseSchemaProvider provider of base NETCONF schemas
     * @param schemaManager manager of device schema resources
     * @param schemaAssembler assembler of device schemas
     * @param deviceActionFactory factory of device actions
     * @param delegate handler receiving device state notifications, typically the topology device facade
     * @param deviceId identifier of the remote device
     * @param nodeId identifier of the topology node
     * @param node NETCONF parameters of the node
     * @param nodeOptional optional NETCONF parameters of the node, may be {@code null}
     * @param sessionInitializer initializer of the NETCONF session on the accepted transport
     * @param negotiatedSshAlg SSH algorithms negotiated with the device, {@code null} for TLS or if not available
     */
    public CallHomeNodeHandler(final NetconfTimer timer,
            final BaseNetconfSchemaProvider baseSchemaProvider, final SchemaResourceManager schemaManager,
            final NetconfTopologySchemaAssembler schemaAssembler,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceHandler delegate,
            final RemoteDeviceId deviceId, final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional,
            final CallHomeSessionInitializer sessionInitializer, final @Nullable NegotiatedSshAlg negotiatedSshAlg) {
        super(timer, baseSchemaProvider, schemaManager, schemaAssembler,
            deviceActionFactory, delegate, deviceId, nodeId, node, nodeOptional);
        this.sessionInitializer = requireNonNull(sessionInitializer);
        // The transport has already been established, hence the negotiated algorithms are known up front and do not
        // change for the lifetime of this handler
        setNegotiatedSshAlg(negotiatedSshAlg);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Attaches this node to the accepted transport through the {@link CallHomeSessionInitializer}. A failure to
     * establish the NETCONF session is reported to the delegate, a cancellation is not.
     *
     * @throws IllegalStateException if this handler has already been connected
     */
    @Override
    public synchronized void connect() {
        if (sessionFuture != null) {
            throw new IllegalStateException("Call-home session for " + deviceId() + " has already been initialized");
        }
        if (isClosed()) {
            return;
        }

        final var future = sessionInitializer.initialize(communicator());
        sessionFuture = future;
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(final NetconfClientSession result) {
                // Nothing to do: the communicator reports the device as connected
            }

            @Override
            public void onFailure(final Throwable cause) {
                if (!(cause instanceof CancellationException)) {
                    onDeviceFailed(cause);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The disconnect is only reported to the delegate. No reconnect is attempted, as only the device can
     * re-establish a call-home connection.
     */
    @Override
    public void onDeviceDisconnected() {
        if (isClosed()) {
            return;
        }
        LOG.debug("Call-home device {} disconnected, waiting for it to call home again", deviceId());
        delegate().onDeviceDisconnected();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The failure is only reported to the delegate. No reconnect is attempted, as only the device can
     * re-establish a call-home connection.
     */
    @Override
    public void onDeviceFailed(final Throwable throwable) {
        if (isClosed()) {
            return;
        }
        LOG.debug("Call-home device {} failed, waiting for it to call home again", deviceId(), throwable);
        delegate().onDeviceFailed(throwable);
    }

    @Override
    synchronized void closeSession() {
        if (sessionFuture != null) {
            sessionFuture.cancel(false);
        }
    }
}
